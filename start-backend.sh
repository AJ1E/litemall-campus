#!/bin/bash
# 启动后端服务脚本

echo "🚀 正在启动后端服务..."
cd /workspaces/litemall-campus

# 检查jar包是否存在
if [ ! -f "litemall-all/target/litemall-all-0.1.0-exec.jar" ]; then
    echo "❌ 未找到jar包，开始构建项目..."
    mvn clean install -DskipTests
fi

echo "✅ 启动 Spring Boot 后端服务 (端口: 8080)..."
java -Dfile.encoding=UTF-8 -jar litemall-all/target/litemall-all-0.1.0-exec.jar
