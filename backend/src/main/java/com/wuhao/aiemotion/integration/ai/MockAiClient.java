package com.wuhao.aiemotion.integration.ai;

public class MockAiClient implements AiClient {

    private final AiMockDataFactory mockDataFactory;

    public MockAiClient(AiMockDataFactory mockDataFactory) {
        this.mockDataFactory = mockDataFactory;
    }

    @Override
    public AiAnalysisResult analyze(AiAnalysisRequest request) {
        return mockDataFactory.buildBaseResult(request);
    }
}
