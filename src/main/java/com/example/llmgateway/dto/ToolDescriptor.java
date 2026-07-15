package com.example.llmgateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 对外暴露的工具描述，供前端展示当前 Agent 已注册了哪些工具。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolDescriptor {
    private String name;
    private String description;
    private Map<String, String> parameters;
    /** 是否为用户通过界面注册的自定义 HTTP 工具；false 表示内置代码工具，不可删除 */
    private boolean custom;
}
