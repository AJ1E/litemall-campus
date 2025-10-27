#!/bin/bash
# 启动轻商城前端脚本

echo "🚀 正在启动轻商城前端..."
cd /workspaces/litemall-campus/litemall-vue

# 检查node_modules是否存在
if [ ! -d "node_modules" ]; then
    echo "❌ 未找到依赖，开始安装..."
    npm install
    npm install --save regenerator-runtime
fi

echo "✅ 启动轻商城前端 (端口: 6255)..."
npm run dev
