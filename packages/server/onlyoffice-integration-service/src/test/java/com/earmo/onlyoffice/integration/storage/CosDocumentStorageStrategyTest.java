package com.earmo.onlyoffice.integration.storage;

import com.earmo.onlyoffice.integration.storage.cos.CosClientFactory;
import com.earmo.onlyoffice.integration.storage.cos.CosDocumentStorageStrategy;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import org.apache.http.client.methods.HttpGet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CosDocumentStorageStrategyTest {

    @Test
    @DisplayName("COS provider 可以写入、读取并删除对象")
    void shouldWriteReadAndDeleteObject() throws IOException {
        COSClient client = mock(COSClient.class);
        CosClientFactory factory = mock(CosClientFactory.class);
        when(factory.client()).thenReturn(client);
        when(factory.bucket()).thenReturn("onlyoffice-documents-1250000000");
        when(client.doesObjectExist("onlyoffice-documents-1250000000", "tenant-a/native/doc-1.docx"))
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);
        when(client.getObjectMetadata("onlyoffice-documents-1250000000", "tenant-a/native/doc-1.docx"))
                .thenReturn(metadata("application/octet-stream", 2L));

        COSObject cosObject = new COSObject();
        cosObject.setObjectContent(new COSObjectInputStream(
                new ByteArrayInputStream("v1".getBytes()),
                new HttpGet("https://cos.example.test/object")
        ));
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(cosObject);

        CosDocumentStorageStrategy strategy = new CosDocumentStorageStrategy(factory);

        StoredObjectResource written = strategy.writeNew(
                new StorageWriteRequest("tenant-a/native/doc-1.docx", "application/octet-stream", "v1".getBytes())
        );
        assertArrayEquals("v1".getBytes(), written.body());
        assertTrue(strategy.exists("tenant-a/native/doc-1.docx"));

        strategy.delete("tenant-a/native/doc-1.docx");
        assertFalse(strategy.exists("tenant-a/native/doc-1.docx"));
        verify(client).deleteObject("onlyoffice-documents-1250000000", "tenant-a/native/doc-1.docx");
    }

    @Test
    @DisplayName("COS provider 应把 SDK 异常转换为 IOException")
    void shouldTranslateCosSdkException() {
        COSClient client = mock(COSClient.class);
        CosClientFactory factory = mock(CosClientFactory.class);
        when(factory.client()).thenReturn(client);
        when(factory.bucket()).thenReturn("onlyoffice-documents-1250000000");
        when(client.doesObjectExist(eq("onlyoffice-documents-1250000000"), eq("tenant-a/native/doc-1.docx")))
                .thenThrow(new CosClientException("boom"));

        CosDocumentStorageStrategy strategy = new CosDocumentStorageStrategy(factory);

        IOException exception = assertThrows(
                IOException.class,
                () -> strategy.exists("tenant-a/native/doc-1.docx")
        );

        assertTrue(exception.getMessage().contains("检查 COS 对象是否存在失败"));
    }

    @Test
    @DisplayName("COS provider 声明的 provider 类型应为 COS")
    void shouldExposeCosProviderType() {
        CosClientFactory factory = mock(CosClientFactory.class);
        CosDocumentStorageStrategy strategy = new CosDocumentStorageStrategy(factory);

        assertEquals(StorageProvider.COS, strategy.provider());
    }

    private ObjectMetadata metadata(String contentType, long contentLength) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(contentLength);
        return metadata;
    }
}
