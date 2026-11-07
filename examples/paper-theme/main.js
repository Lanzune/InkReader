var inkPlugin = {
    id: "dev.inkreader.example.paper",
    theme: {
        id: "example.paper",
        name: "Example Paper",
        background: "#F4EBDD",
        foreground: "#332B24",
        accent: "#7A5230",
        dark: false
    },
    command: {
        title: "显示阅读进度",
        render: function (progress) {
            return "示例插件进度：" + Math.round(progress * 100) + "%";
        }
    }
};
