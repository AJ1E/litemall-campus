#!/bin/bash
# 启动管理后台前端脚本

echo "🚀 正在启动管理后台前端..."
cd /workspaces/litemall-campus/litemall-admin

# 检查node_modules是否存在
if [ ! -d "node_modules" ]; then
    echo "❌ 未找到依赖，开始安装..."
    npm install
fi

echo "✅ 启动管理后台前端 (端口: 9527)..."
npm run dev
