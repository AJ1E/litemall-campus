#!/bin/bash
# 停止所有服务脚本

echo "🛑 正在停止所有服务..."

# 检查是否使用tmux
if tmux has-session -t litemall 2>/dev/null; then
    echo "📋 检测到 tmux session，正在关闭..."
    tmux kill-session -t litemall
    echo "✅ tmux session 已关闭"
else
    # 停止后台进程
    if [ -f "logs/backend.pid" ]; then
        BACKEND_PID=$(cat logs/backend.pid)
        echo "🔴 停止后端服务 (PID: $BACKEND_PID)..."
        kill $BACKEND_PID 2>/dev/null || echo "   进程已停止"
        rm logs/backend.pid
    fi
    
    if [ -f "logs/admin.pid" ]; then
        ADMIN_PID=$(cat logs/admin.pid)
        echo "🔴 停止管理后台 (PID: $ADMIN_PID)..."
        kill $ADMIN_PID 2>/dev/null || echo "   进程已停止"
        rm logs/admin.pid
    fi
    
    if [ -f "logs/vue.pid" ]; then
        VUE_PID=$(cat logs/vue.pid)
        echo "🔴 停止轻商城 (PID: $VUE_PID)..."
        kill $VUE_PID 2>/dev/null || echo "   进程已停止"
        rm logs/vue.pid
    fi
fi

# 强制杀死可能残留的进程
echo "🧹 清理残留进程..."
pkill -f "litemall-all-0.1.0-exec.jar" 2>/dev/null
pkill -f "vue-cli-service serve" 2>/dev/null

echo "✅ 所有服务已停止！"
