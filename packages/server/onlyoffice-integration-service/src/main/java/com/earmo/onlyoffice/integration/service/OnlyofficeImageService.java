package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.model.InsertImageResponse;
import com.earmo.onlyoffice.integration.model.RemoteImageResource;
import java.io.IOException;

/**
 * ONLYOFFICE 图片插入服务契约。
 */
public interface OnlyofficeImageService {

  InsertImageResponse buildInsertImageResponse(String documentId, String sourceUrl);

  RemoteImageResource proxyRemoteImage(String sourceUrl) throws IOException;
}
