#!/bin/bash
# Epic 3 功能测试脚本
# 使用方式: ./epic-3-test.sh

echo "========================================="
echo "Epic 3 交易功能集成测试"
echo "========================================="
echo ""

# 配置
BASE_URL="http://localhost:8080"
WX_API="${BASE_URL}/wx"
ADMIN_API="${BASE_URL}/admin"

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试结果统计
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 测试函数
test_api() {
    local test_name="$1"
    local method="$2"
    local url="$3"
    local data="$4"
    local expected_code="$5"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo -n "测试 $TOTAL_TESTS: $test_name ... "
    
    if [ "$method" == "GET" ]; then
        response=$(curl -s -w "\n%{http_code}" -X GET "$url")
    elif [ "$method" == "POST" ]; then
        response=$(curl -s -w "\n%{http_code}" -X POST "$url" \
            -H "Content-Type: application/json" \
            -d "$data")
    elif [ "$method" == "DELETE" ]; then
        response=$(curl -s -w "\n%{http_code}" -X DELETE "$url")
    fi
    
    http_code=$(echo "$response" | tail -n 1)
    body=$(echo "$response" | head -n -1)
    
    if [ "$http_code" == "$expected_code" ]; then
        echo -e "${GREEN}PASS${NC} (HTTP $http_code)"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}FAIL${NC} (期望: $expected_code, 实际: $http_code)"
        echo "    响应: $body"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
}

# 检查服务是否启动
echo "1. 检查服务状态..."
if curl -s "${BASE_URL}/wx/index/index" > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} 服务正常运行"
else
    echo -e "${RED}✗${NC} 服务未启动或不可访问"
    echo "请先启动应用: cd litemall-all && java -jar target/litemall-all-0.1.0-exec.jar"
    exit 1
fi
echo ""

# 2. 评价标签接口测试
echo "2. 评价标签接口测试"
test_api "获取评价标签列表" "GET" "${WX_API}/sicau/comment/tags" "" "200"
test_api "获取买家评价标签" "GET" "${WX_API}/sicau/comment/tags?role=1" "" "200"
test_api "获取卖家评价标签" "GET" "${WX_API}/sicau/comment/tags?role=2" "" "200"
echo ""

# 3. 评价接口测试（需要登录token）
echo "3. 评价接口测试"
echo -e "${YELLOW}注意: 以下测试需要有效的登录token，预期返回401未登录${NC}"
test_api "查询收到的评价" "GET" "${WX_API}/sicau/comment/received?page=1&limit=10" "" "401"
test_api "查询发出的评价" "GET" "${WX_API}/sicau/comment/sent?page=1&limit=10" "" "401"
test_api "发布评价(未登录)" "POST" "${WX_API}/sicau/comment/post" \
    '{"orderId":1,"toUserId":2,"rating":5,"content":"很好"}' "401"
echo ""

# 4. 举报接口测试
echo "4. 举报接口测试"
test_api "提交举报(未登录)" "POST" "${WX_API}/sicau/report/submit" \
    '{"orderId":1,"reportedId":2,"type":1,"reason":"描述不符"}' "401"
test_api "查询我的举报" "GET" "${WX_API}/sicau/report/my?page=1&limit=10" "" "401"
echo ""

# 5. 退款接口测试
echo "5. 退款接口测试"
test_api "申请退款(未登录)" "POST" "${WX_API}/sicau/refund/apply" \
    '{"orderId":1,"refundAmount":50.00,"refundReason":"不想要了"}' "401"
test_api "查询订单退款" "GET" "${WX_API}/sicau/refund/order/1" "" "401"
echo ""

# 6. 管理端接口测试（需要管理员token）
echo "6. 管理端接口测试"
echo -e "${YELLOW}注意: 以下测试需要管理员token，预期返回401或403${NC}"
test_api "管理员查询举报列表" "GET" "${ADMIN_API}/sicau/report/list?page=1&limit=10" "" "401"
test_api "管理员查询退款列表" "GET" "${ADMIN_API}/sicau/refund/list?page=1&limit=10" "" "401"
test_api "管理员查询标签列表" "GET" "${ADMIN_API}/sicau/comment/tags" "" "401"
echo ""

# 7. 支付回调接口测试
echo "7. 支付回调接口测试"
echo -e "${YELLOW}注意: 支付回调需要微信签名，预期处理失败${NC}"
test_api "微信支付回调" "POST" "${BASE_URL}/wx/pay/payNotify" \
    '<xml><return_code>SUCCESS</return_code></xml>' "200"
echo ""

# 8. 数据库连接测试
echo "8. 数据库表结构验证"
echo "正在检查数据库表是否存在..."

# 这里需要数据库连接信息
DB_HOST="gateway01.eu-central-1.prod.aws.tidbcloud.com"
DB_PORT="4000"
DB_NAME="litemall"
DB_USER="<从配置文件读取>"

if command -v mysql &> /dev/null; then
    echo -e "${YELLOW}提示: 请手动验证数据库表${NC}"
    echo "  sicau_comment"
    echo "  sicau_report"
    echo "  sicau_order_refund"
    echo "  sicau_comment_tags"
    echo ""
    echo "SQL验证命令:"
    echo "  SELECT COUNT(*) FROM sicau_comment_tags;"
    echo "  SHOW COLUMNS FROM litemall_order LIKE 'seller_id';"
else
    echo -e "${YELLOW}未安装mysql客户端，跳过数据库验证${NC}"
fi
echo ""

# 9. 定时任务测试（仅检查类是否存在）
echo "9. 定时任务类验证"
if [ -f "litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/task/OrderStatusTask.java" ]; then
    echo -e "${GREEN}✓${NC} OrderStatusTask.java 存在"
    echo "  定时任务列表:"
    echo "    - cancelUnpaidOrders (每5分钟)"
    echo "    - remindUnshippedOrders (每小时)"
    echo "    - autoConfirmReceivedOrders (每天2:00)"
    echo "    - autoCloseCommentOrders (每天3:00)"
else
    echo -e "${RED}✗${NC} OrderStatusTask.java 不存在"
fi
echo ""

# 10. 支付服务类验证
echo "10. 支付服务类验证"
if [ -f "litemall-wx-api/src/main/java/org/linlinjava/litemall/wx/service/WxOrderPayService.java" ]; then
    echo -e "${GREEN}✓${NC} WxOrderPayService.java 存在"
    echo "  支付功能:"
    echo "    - createPayOrder() - 创建支付订单"
    echo "    - handlePayNotify() - 处理支付回调"
    echo "    - refund() - 发起退款"
else
    echo -e "${RED}✗${NC} WxOrderPayService.java 不存在"
fi
echo ""

# 测试结果统计
echo "========================================="
echo "测试结果统计"
echo "========================================="
echo "总测试数: $TOTAL_TESTS"
echo -e "${GREEN}通过: $PASSED_TESTS${NC}"
echo -e "${RED}失败: $FAILED_TESTS${NC}"
echo ""

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}所有测试通过！ 🎉${NC}"
    exit 0
else
    echo -e "${YELLOW}部分测试失败，请检查日志${NC}"
    exit 1
fi
