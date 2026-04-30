package com.earmo.onlyoffice.integration.service;

/**
 * ONLYOFFICE Document Server Command Service 契约。
 *
 * <p>通过 Command Service API 向 ONLYOFFICE Document Server 发送运行态指令，
 * 例如强制保存当前文档，使 Document Server 立即触发 callback 回写存储。
 */
public interface OnlyofficeCommandService {

    /**
     * 向 ONLYOFFICE Document Server 发送 forcesave 命令（fire-and-forget）。
     *
     * <p>调用后 Document Server 会对指定文档触发 callback（status 6），
     * 后端收到 callback 后执行实际的文件下载和存储回写。
     *
     * @param documentId 文档内部主键
     */
    void forceSave(String documentId);

    /**
     * 发送 forcesave 命令并同步等待 callback 回写完成。
     *
     * <p>内部会注册一个等待锁，发送 forcesave 后阻塞当前线程，直到
     * {@link #notifySaveCompleted(String)} 被调用或超时。
     *
     * @param documentId    文档内部主键
     * @param timeoutMillis 最大等待毫秒数
     * @return 如果在超时前收到保存完成通知则返回 {@code true}
     */
    boolean forceSaveAndAwait(String documentId, long timeoutMillis);

    /**
     * 由 callback 端点在文件回写成功后调用，唤醒 {@link #forceSaveAndAwait} 的阻塞线程。
     *
     * @param documentId 文档内部主键
     */
    void notifySaveCompleted(String documentId);
}
