package com.example.picturezipupload.domain;

/**
 * 图片标注流程状态。
 */
public enum PictureStatus {
    /**
     * 待标注，图片首次导入后的默认状态。
     */
    MARK,

    /**
     * 被过滤舍弃。
     */
    FILTER,

    /**
     * 待矫正。
     */
    EDIT,

    /**
     * 已标注。
     */
    RELEASE,

    /**
     * 已分发。
     */
    SEND
}
