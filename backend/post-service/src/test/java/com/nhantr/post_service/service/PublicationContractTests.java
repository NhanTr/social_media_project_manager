package com.nhantr.post_service.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class PublicationContractTests {
    @Test
    void acceptsMobilePublicationKeyAndUploadBinding() {
        var request = JsonMapper.builder().build().readValue("""
                {"mediaType":"IMAGE","mediaUrl":"http://media/photo.jpg",
                 "clientRequestId":"b018e65a-454a-4d83-9104-5439a21d4e6c","uploadId":12}
                """, PostService.CreatePostRequest.class);
        assertThat(JsonMapper.builder().build().valueToTree(request).has("clientRequestId")).isTrue();
        assertThat(JsonMapper.builder().build().valueToTree(request).has("uploadId")).isTrue();
    }
}
