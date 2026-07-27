package com.plug.http.response;

import java.net.http.HttpHeaders;

public record PlugHttpResponse<T>(int statusCode, HttpHeaders headers, T body) {
}
