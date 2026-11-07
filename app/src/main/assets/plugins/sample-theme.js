// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

var inkPlugin = {
    id: "sample.paper",
    theme: {
        id: "sample.paper",
        name: "Paper",
        background: "#F3E9D2",
        foreground: "#3A2E24",
        accent: "#8B5E34",
        dark: false
    },
    command: {
        title: "示例命令：显示进度",
        render: function (progress) {
            return "插件命令：阅读进度 " + Math.round(progress * 100) + "%";
        }
    }
};
