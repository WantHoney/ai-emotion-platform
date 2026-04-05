#!/usr/bin/env python3
"""Prepare or verify a local Stable Diffusion pipeline and optionally generate images.

Default behavior intentionally stops after:
1. optional model download
2. pipeline construction
3. optional device placement / offload setup

When --generate is supplied, it will run a single inference pass and save the
result to disk.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

import torch

from huggingface_hub import snapshot_download


DEFAULT_SD3_MODEL_ID = "stabilityai/stable-diffusion-3.5-medium"
DEFAULT_SDXL_MODEL_ID = "stabilityai/stable-diffusion-xl-base-1.0"
DEFAULT_SD3_LOCAL_DIR = Path("models/stable-diffusion-3.5-medium")
DEFAULT_SDXL_LOCAL_DIR = Path("models/stable-diffusion-xl-base-1.0")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Download and initialize a local Stable Diffusion pipeline without generating images.",
    )
    parser.add_argument(
        "--profile",
        choices=("sd3", "sdxl"),
        default="sd3",
        help="Pipeline profile to initialize. Default targets Stable Diffusion 3.5 Medium.",
    )
    parser.add_argument(
        "--prompt",
        default="A calm placeholder prompt for initialization only",
        help="Accepted for future inference workflows; this script does not generate an image.",
    )
    parser.add_argument(
        "--model-id",
        default=None,
        help="Override the default Hugging Face model repo id for the selected profile.",
    )
    parser.add_argument(
        "--local-dir",
        default=None,
        help="Local model directory. Defaults to models/<selected-profile>.",
    )
    parser.add_argument(
        "--download",
        action="store_true",
        help="Download the selected model repo into --local-dir before loading.",
    )
    parser.add_argument(
        "--allow-remote",
        action="store_true",
        help="Allow loading from the remote repo id when a local snapshot does not exist.",
    )
    parser.add_argument(
        "--device-strategy",
        choices=("auto", "cuda", "offload", "cpu"),
        default="auto",
        help="How to prepare the initialized pipeline after loading.",
    )
    parser.add_argument(
        "--dtype",
        choices=("float16", "bfloat16", "float32"),
        default="float16",
        help="Torch dtype used while loading the pipeline.",
    )
    parser.add_argument(
        "--generate",
        action="store_true",
        help="Run a single inference pass after initialization and save the result.",
    )
    parser.add_argument(
        "--negative-prompt",
        default="",
        help="Negative prompt used when --generate is enabled.",
    )
    parser.add_argument(
        "--steps",
        type=int,
        default=24,
        help="Number of inference steps used when --generate is enabled.",
    )
    parser.add_argument(
        "--guidance-scale",
        type=float,
        default=5.0,
        help="CFG guidance scale used when --generate is enabled.",
    )
    parser.add_argument(
        "--width",
        type=int,
        default=1536,
        help="Output width used when --generate is enabled.",
    )
    parser.add_argument(
        "--height",
        type=int,
        default=896,
        help="Output height used when --generate is enabled.",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=3407,
        help="Random seed used when --generate is enabled.",
    )
    parser.add_argument(
        "--out",
        default=None,
        help="Output image path used when --generate is enabled.",
    )
    return parser.parse_args()


def get_defaults(profile: str) -> tuple[str, Path]:
    if profile == "sd3":
        return DEFAULT_SD3_MODEL_ID, DEFAULT_SD3_LOCAL_DIR
    return DEFAULT_SDXL_MODEL_ID, DEFAULT_SDXL_LOCAL_DIR


def resolve_dtype(name: str) -> torch.dtype:
    return {
        "float16": torch.float16,
        "bfloat16": torch.bfloat16,
        "float32": torch.float32,
    }[name]


def get_hf_token() -> str | None:
    return os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_HUB_TOKEN")


def gpu_summary() -> dict[str, Any]:
    summary: dict[str, Any] = {
        "cuda_available": torch.cuda.is_available(),
        "device_count": torch.cuda.device_count(),
    }
    if torch.cuda.is_available():
        props = torch.cuda.get_device_properties(0)
        summary.update(
            {
                "device_name": torch.cuda.get_device_name(0),
                "total_vram_gb": round(props.total_memory / (1024**3), 2),
                "cuda_runtime": torch.version.cuda,
            }
        )
    else:
        summary["device_name"] = None
        summary["total_vram_gb"] = None
        summary["cuda_runtime"] = None
    return summary


def download_model(repo_id: str, local_dir: Path, token: str | None) -> Path:
    local_dir.mkdir(parents=True, exist_ok=True)
    allow_patterns = get_allow_patterns(repo_id)
    path = snapshot_download(
        repo_id=repo_id,
        local_dir=str(local_dir),
        token=token,
        allow_patterns=allow_patterns,
    )
    return Path(path)


def get_allow_patterns(repo_id: str) -> list[str]:
    if repo_id == DEFAULT_SDXL_MODEL_ID:
        return [
            "model_index.json",
            "scheduler/*",
            "tokenizer/*",
            "tokenizer_2/*",
            "text_encoder/config.json",
            "text_encoder/model*.safetensors",
            "text_encoder_2/config.json",
            "text_encoder_2/model*.safetensors",
            "unet/config.json",
            "unet/diffusion_pytorch_model*.safetensors",
            "vae/config.json",
            "vae/diffusion_pytorch_model*.safetensors",
        ]

    if repo_id == DEFAULT_SD3_MODEL_ID:
        return [
            "model_index.json",
            "scheduler/*",
            "tokenizer/*",
            "tokenizer_2/*",
            "tokenizer_3/*",
            "text_encoder/config.json",
            "text_encoder/model*.json",
            "text_encoder/model*.safetensors",
            "text_encoder_2/config.json",
            "text_encoder_2/model*.json",
            "text_encoder_2/model*.safetensors",
            "text_encoder_3/config.json",
            "text_encoder_3/model*.json",
            "text_encoder_3/model*.safetensors",
            "transformer/config.json",
            "transformer/diffusion_pytorch_model*.json",
            "transformer/diffusion_pytorch_model*.safetensors",
            "vae/config.json",
            "vae/diffusion_pytorch_model*.json",
            "vae/diffusion_pytorch_model*.safetensors",
        ]

    return ["*"]


def load_pipeline(profile: str, source: str, dtype: torch.dtype):
    if profile == "sd3":
        from diffusers import StableDiffusion3Pipeline

        return StableDiffusion3Pipeline.from_pretrained(
            source,
            torch_dtype=dtype,
            local_files_only=Path(source).exists(),
            low_cpu_mem_usage=True,
            use_safetensors=True,
        )

    from diffusers import DiffusionPipeline

    return DiffusionPipeline.from_pretrained(
        source,
        torch_dtype=dtype,
        use_safetensors=True,
        variant="fp16" if dtype == torch.float16 else None,
        local_files_only=Path(source).exists(),
        low_cpu_mem_usage=True,
    )


def prepare_pipeline(pipe, strategy: str) -> str:
    if hasattr(pipe, "enable_attention_slicing"):
        pipe.enable_attention_slicing()
    if hasattr(pipe, "enable_vae_slicing"):
        pipe.enable_vae_slicing()
    if hasattr(pipe, "enable_vae_tiling"):
        pipe.enable_vae_tiling()

    if strategy == "auto":
        strategy = "offload" if torch.cuda.is_available() else "cpu"

    if strategy == "cpu" or not torch.cuda.is_available():
        pipe.to("cpu")
        return "cpu"

    if strategy == "cuda":
        pipe.to("cuda")
        return "cuda"

    pipe.enable_model_cpu_offload()
    return "offload"


def build_generator(device_strategy: str, seed: int) -> torch.Generator:
    generator_device = "cuda" if device_strategy == "cuda" and torch.cuda.is_available() else "cpu"
    return torch.Generator(device=generator_device).manual_seed(seed)


def run_generation(pipe, args: argparse.Namespace, device_strategy: str) -> Path:
    if not args.out:
        raise ValueError("--out is required when --generate is enabled.")

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    generator = build_generator(device_strategy, args.seed)
    call_kwargs: dict[str, Any] = {
        "prompt": args.prompt,
        "negative_prompt": args.negative_prompt or None,
        "num_inference_steps": args.steps,
        "guidance_scale": args.guidance_scale,
        "width": args.width,
        "height": args.height,
        "generator": generator,
    }
    if args.profile == "sd3":
        call_kwargs["max_sequence_length"] = 256

    if torch.cuda.is_available():
        torch.cuda.empty_cache()

    with torch.inference_mode():
        result = pipe(**call_kwargs)

    image = result.images[0]
    image.save(out_path)

    if torch.cuda.is_available():
        torch.cuda.empty_cache()

    return out_path


def main() -> int:
    args = parse_args()
    default_model_id, default_local_dir = get_defaults(args.profile)
    model_id = args.model_id or default_model_id
    local_dir = Path(args.local_dir) if args.local_dir else default_local_dir
    dtype = resolve_dtype(args.dtype)
    token = get_hf_token()

    result: dict[str, Any] = {
        "profile": args.profile,
        "prompt_received": args.prompt,
        "model_id": model_id,
        "local_dir": str(local_dir),
        "download_requested": args.download,
        "allow_remote": args.allow_remote,
        "hf_token_present": bool(token),
        "gpu": gpu_summary(),
    }

    try:
        if args.download:
            snapshot_path = download_model(model_id, local_dir, token)
            result["downloaded_to"] = str(snapshot_path)

        source = None
        if local_dir.exists() and any(local_dir.iterdir()):
            source = str(local_dir)
        elif args.allow_remote:
            source = model_id
        else:
            raise FileNotFoundError(
                f"Local model directory not found or empty: {local_dir}. "
                "Use --download or pass --allow-remote."
            )

        pipe = load_pipeline(args.profile, source, dtype)
        result["pipeline_class"] = pipe.__class__.__name__
        result["load_source"] = source
        result["dtype"] = str(dtype).replace("torch.", "")
        device_strategy = prepare_pipeline(pipe, args.device_strategy)
        result["device_strategy"] = device_strategy
        result["status"] = "ready"

        if args.generate:
            output_path = run_generation(pipe, args, device_strategy)
            result["generated"] = {
                "output_path": str(output_path),
                "steps": args.steps,
                "guidance_scale": args.guidance_scale,
                "width": args.width,
                "height": args.height,
                "seed": args.seed,
            }

        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except Exception as exc:  # pragma: no cover - smoke test failure path
        result["status"] = "error"
        result["error_type"] = exc.__class__.__name__
        result["error"] = str(exc)
        print(json.dumps(result, ensure_ascii=False, indent=2), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
