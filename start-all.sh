#!/bin/bash
# 一键启动所有项目脚本

echo "═══════════════════════════════════════════════════"
echo "   🚀 Litemall 项目一键启动脚本"
echo "═══════════════════════════════════════════════════"
echo ""

# 获取脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# 检查是否在tmux或screen中
if command -v tmux &> /dev/null; then
    USE_TMUX=true
    echo "✅ 检测到 tmux，将使用 tmux 管理多个终端"
else
    USE_TMUX=false
    echo "ℹ️  未安装 tmux，将使用后台进程方式启动"
fi

echo ""
echo "📋 启动顺序："
echo "  1️⃣  后端服务 (Spring Boot) - 端口 8080"
echo "  2️⃣  管理后台前端 (Vue.js) - 端口 9527"
echo "  3️⃣  轻商城前端 (Vue.js) - 端口 6255"
echo ""

if [ "$USE_TMUX" = true ]; then
    # 使用tmux方式
    SESSION_NAME="litemall"
    
    # 检查session是否已存在
    if tmux has-session -t "$SESSION_NAME" 2>/dev/null; then
        echo "⚠️  检测到已存在的 tmux session: $SESSION_NAME"
        read -p "是否要关闭并重新启动? (y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            tmux kill-session -t "$SESSION_NAME"
        else
            echo "❌ 取消启动"
            exit 0
        fi
    fi
    
    echo ""
    echo "🔧 创建 tmux session: $SESSION_NAME"
    
    # 创建新session并启动后端
    tmux new-session -d -s "$SESSION_NAME" -n backend "bash $SCRIPT_DIR/start-backend.sh"
    echo "✅ [1/3] 后端服务已在 tmux 窗口启动"
    
    # 等待后端启动
    echo "⏳ 等待后端服务启动 (30秒)..."
    sleep 30
    
    # 创建新窗口启动管理后台
    tmux new-window -t "$SESSION_NAME" -n admin "bash $SCRIPT_DIR/start-admin.sh"
    echo "✅ [2/3] 管理后台已在 tmux 窗口启动"
    
    # 等待管理后台启动
    echo "⏳ 等待管理后台启动 (10秒)..."
    sleep 10
    
    # 创建新窗口启动轻商城
    tmux new-window -t "$SESSION_NAME" -n vue "bash $SCRIPT_DIR/start-vue.sh"
    echo "✅ [3/3] 轻商城已在 tmux 窗口启动"
    
    echo ""
    echo "═══════════════════════════════════════════════════"
    echo "  ✨ 所有服务已启动！"
    echo "═══════════════════════════════════════════════════"
    echo ""
    echo "📍 访问地址："
    echo "   后端 API:    http://localhost:8080"
    echo "   管理后台:    http://localhost:9527"
    echo "   轻商城:      http://localhost:6255"
    echo ""
    echo "📝 tmux 操作指南："
    echo "   查看所有窗口:  tmux attach -t $SESSION_NAME"
    echo "   切换窗口:      Ctrl+b 然后按数字键 (0/1/2)"
    echo "   退出窗口:      Ctrl+b d (detach)"
    echo "   停止所有服务:  tmux kill-session -t $SESSION_NAME"
    echo ""
    echo "💡 执行以下命令进入 tmux 查看日志："
    echo "   tmux attach -t $SESSION_NAME"
    echo ""
    
else
    # 使用后台进程方式
    echo "🔧 使用后台进程方式启动..."
    
    # 创建日志目录
    mkdir -p logs
    
    # 启动后端
    echo "▶️  [1/3] 启动后端服务..."
    bash "$SCRIPT_DIR/start-backend.sh" > logs/backend.log 2>&1 &
    BACKEND_PID=$!
    echo "   PID: $BACKEND_PID (日志: logs/backend.log)"
    
    # 等待后端启动
    echo "⏳ 等待后端服务启动 (30秒)..."
    sleep 30
    
    # 启动管理后台
    echo "▶️  [2/3] 启动管理后台..."
    bash "$SCRIPT_DIR/start-admin.sh" > logs/admin.log 2>&1 &
    ADMIN_PID=$!
    echo "   PID: $ADMIN_PID (日志: logs/admin.log)"
    
    # 等待管理后台启动
    echo "⏳ 等待管理后台启动 (10秒)..."
    sleep 10
    
    # 启动轻商城
    echo "▶️  [3/3] 启动轻商城..."
    bash "$SCRIPT_DIR/start-vue.sh" > logs/vue.log 2>&1 &
    VUE_PID=$!
    echo "   PID: $VUE_PID (日志: logs/vue.log)"
    
    # 保存PID到文件
    echo "$BACKEND_PID" > logs/backend.pid
    echo "$ADMIN_PID" > logs/admin.pid
    echo "$VUE_PID" > logs/vue.pid
    
    echo ""
    echo "═══════════════════════════════════════════════════"
    echo "  ✨ 所有服务已启动！"
    echo "═══════════════════════════════════════════════════"
    echo ""
    echo "📍 访问地址："
    echo "   后端 API:    http://localhost:8080"
    echo "   管理后台:    http://localhost:9527"
    echo "   轻商城:      http://localhost:6255"
    echo ""
    echo "📝 进程信息："
    echo "   后端服务 PID: $BACKEND_PID"
    echo "   管理后台 PID: $ADMIN_PID"
    echo "   轻商城 PID:   $VUE_PID"
    echo ""
    echo "📋 日志文件："
    echo "   后端:   tail -f logs/backend.log"
    echo "   管理后台: tail -f logs/admin.log"
    echo "   轻商城: tail -f logs/vue.log"
    echo ""
    echo "🛑 停止所有服务："
    echo "   bash stop-all.sh"
    echo ""
fi
