package com.signalops.core.incident.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiConnectionTestService {

    private final ChatClient chatClient;

    public AiConnectionTestService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }



    public String ask(String question) {
        var response = chatClient.prompt()
                .system("""
                    You are a backend engineering assistant.
                    Answer accurately and concisely.
                    If you are uncertain, say so rather than inventing facts.
                    """)
                .user(question)
                .call()
                .chatResponse();

        if (response == null
                || response.getResult() == null
                || response.getResult().getOutput() == null) {
            throw new IllegalStateException(
                    "AI returned no usable response");
        }

        String content = response.getResult()
                .getOutput()
                .getText();

        if (content == null || content.isBlank()) {
            throw new IllegalStateException(
                    "AI returned empty text content");
        }

        return content;
    }

}