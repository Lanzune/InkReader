// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 InkReader contributors

var inkPlugin = {
    id: "dev.inkreader.page-number",
    progress: {
        render: function (state) {
            if (state.pageCount > 0 && state.page >= 0 && state.page < state.pageCount) {
                return (state.page + 1) + "/" + state.pageCount;
            }
            if (state.totalChars > 0) {
                return state.percent.toFixed(1) + "%";
            }
            return "…";
        }
    }
};
