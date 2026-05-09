package com.earmo.onlyoffice.integration.storage;

import com.earmo.onlyoffice.integration.storage.model.StorageWriteRequest;
import com.earmo.onlyoffice.integration.storage.model.StoredObjectResource;
import com.earmo.onlyoffice.integration.storage.enums.StorageProvider;

import java.io.IOException;

/**
 * 统一抽象文档对象存储能力。
 *
 * <p>业务层只关心“读、写、覆盖、删除、存在性”，不直接关心 provider 背后是本地目录还是对象存储。
 */
public interface DocumentStorageStrategy {

    /**
     * 返回当前策略支持的存储 provider。
     *
     * <p>上层服务通过该值把 resolver 解析出的 provider 路由到具体实现。
     *
     * @return 当前实现对应的存储 provider
     */
    StorageProvider provider();

    /**
     * 判断指定对象键是否已经存在。
     *
     * @param storageKey provider-neutral 的对象键，通常由 {@link StorageKeyFactory} 生成
     * @return 对象存在时返回 {@code true}，不存在时返回 {@code false}
     * @throws IOException provider 访问失败、网络异常或本地文件系统异常时抛出
     */
    boolean exists(String storageKey) throws IOException;

    /**
     * 读取指定对象的完整内容和元信息。
     *
     * <p>实现需要把不同 provider 的读取结果统一转换为 {@link StoredObjectResource}，
     * 让上层无需感知对象来自本地磁盘、MinIO、COS 或其他存储。
     *
     * @param storageKey provider-neutral 的对象键
     * @return 包含对象内容、大小、内容类型、修改时间等信息的统一资源视图
     * @throws IOException 对象不存在或 provider 读取失败时抛出
     */
    StoredObjectResource read(String storageKey) throws IOException;

    /**
     * 写入一个新对象。
     *
     * <p>语义上用于“首次创建”。支持强创建语义的 provider 应在对象已存在时失败；
     * 无法原生区分创建和覆盖的 provider 需要在实现或上层编排中保持一致行为。
     *
     * @param request 对象键、内容类型和字节内容
     * @return 写入后的统一资源视图
     * @throws IOException 写入失败、对象冲突或 provider 访问异常时抛出
     */
    StoredObjectResource writeNew(StorageWriteRequest request) throws IOException;

    /**
     * 覆盖写入已有对象。
     *
     * <p>该方法用于保存回调、版本更新或重新上传等场景。实现应尽量保证覆盖过程
     * 对读取方可见的是完整对象，避免产生半写入内容。
     *
     * @param request 对象键、内容类型和新的字节内容
     * @return 覆盖完成后的统一资源视图
     * @throws IOException 覆盖失败或 provider 访问异常时抛出
     */
    StoredObjectResource overwrite(StorageWriteRequest request) throws IOException;

    /**
     * 删除指定对象。
     *
     * <p>删除操作建议保持幂等：对象已经不存在时不应阻断业务流程。
     *
     * @param storageKey provider-neutral 的对象键
     * @throws IOException provider 删除失败或本地文件系统异常时抛出
     */
    void delete(String storageKey) throws IOException;
}
