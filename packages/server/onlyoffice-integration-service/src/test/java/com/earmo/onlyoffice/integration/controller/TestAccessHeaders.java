package com.earmo.onlyoffice.integration.controller;

import org.springframework.http.HttpHeaders;

final class TestAccessHeaders {

    private TestAccessHeaders() {
    }

    static HttpHeaders headers(String actorUser, String actorName) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Tenant-Id", "native");
        headers.add("X-Source-System", "native");
        headers.add("X-External-User-Id", actorUser);
        headers.add("X-User-Display-Name", actorName);
        headers.add("X-Access-Permissions", "edit=true,download=true,comment=true,print=true");
        return headers;
    }
}
