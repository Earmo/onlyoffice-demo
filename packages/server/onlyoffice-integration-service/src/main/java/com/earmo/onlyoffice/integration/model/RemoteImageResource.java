package com.earmo.onlyoffice.integration.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.MediaType;

/**
 * 远程图片代理结果。
 *
 * @param body      图片字节内容
 * @param mediaType 图片媒体类型
 * @param filename  返回给浏览器或 ONLYOFFICE 的文件名
 */
@Schema(description = "代理远程图片后的内部资源对象。")
public record RemoteImageResource(
        @Schema(description = "图片的二进制内容。")
        byte[] body,
        @Schema(description = "图片媒体类型。")
        MediaType mediaType,
        @Schema(description = "返回给浏览器或 ONLYOFFICE 的文件名。", example = "logo.png")
        String filename
) {
}



