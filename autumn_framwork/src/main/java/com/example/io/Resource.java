package com.example.io;

/**
 * record 关键字用来定义不可变类
 * 
 * 该类表示一个文件对象，包含
 * @param path 文件路径
 * @param name 文件名
 * 
 * @author jw
 * @date 2026-1-15
 */
public record Resource(String path, String name) {

}
