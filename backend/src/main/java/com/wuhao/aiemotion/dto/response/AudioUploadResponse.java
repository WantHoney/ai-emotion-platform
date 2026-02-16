package com.wuhao.aiemotion.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AudioUploadResponse {
    private Long audioId;
    private String originalName;
    private String fileName;      // 服务器保存的文件名
    private String downloadUrl;   // 可访问的URL
}
