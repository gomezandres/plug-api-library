package com.plug.http.demo;

import com.plug.http.client.PlugHttpClient;

public class ManualRunDemo {

    public static void main(String[] args) {
        PlugHttpClient client = PlugHttpClient.builder()
            .baseUri("https://app.beeceptor.com")
            .logSink(event -> System.out.println("[log] " + event))
            .build();

        var response = client.get("/console/prueba-http-client-library")
            .throwOnError(false)
            .executeString();

        System.out.println("Status: " + response.statusCode());
        System.out.println("Body: " + response.body());
    }
}
