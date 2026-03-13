#!/usr/bin/env bash
set -euo pipefail

# 用途：批量插入测试商品
# 默认：插入 24 条
# 依赖：本机可执行 mysql 命令，且可访问 freshmall_db
#
# 用法：
#   bash doc/seed_things_for_waterfall.sh
#   bash doc/seed_things_for_waterfall.sh --count 40
#   bash doc/seed_things_for_waterfall.sh --prefix "[实验瀑布]"
#   bash doc/seed_things_for_waterfall.sh --cleanup
#   bash doc/seed_things_for_waterfall.sh --host 127.0.0.1 --port 3306 --user root --password root

COUNT=24
PREFIX="[新疆]"
DB_HOST="127.0.0.1"
DB_PORT="3306"
DB_USER="root"
DB_PASS="root"
DB_NAME="freshmall_db"
CLEANUP_ONLY="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --count)
      COUNT="$2"; shift 2 ;;
    --prefix)
      PREFIX="$2"; shift 2 ;;
    --host)
      DB_HOST="$2"; shift 2 ;;
    --port)
      DB_PORT="$2"; shift 2 ;;
    --user)
      DB_USER="$2"; shift 2 ;;
    --password)
      DB_PASS="$2"; shift 2 ;;
    --db)
      DB_NAME="$2"; shift 2 ;;
    --cleanup)
      CLEANUP_ONLY="true"; shift 1 ;;
    *)
      echo "未知参数: $1" >&2
      exit 1
      ;;
  esac
done

MYSQL_CMD=(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" --default-character-set=utf8mb4)

sql_escape() {
  printf '%s' "$1" | sed "s/'/''/g"
}

PREFIX_ESCAPED=$(sql_escape "$PREFIX")

if [[ "$CLEANUP_ONLY" == "true" ]]; then
  echo "[INFO] 清理测试数据：title 以 $PREFIX 开头"
  "${MYSQL_CMD[@]}" -e "DELETE FROM b_thing WHERE title LIKE '${PREFIX_ESCAPED}%';"
  echo "[OK] 清理完成"
  exit 0
fi

if ! [[ "$COUNT" =~ ^[0-9]+$ ]]; then
  echo "--count 必须是正整数" >&2
  exit 1
fi

if [[ "$COUNT" -le 0 ]]; then
  echo "--count 必须大于 0" >&2
  exit 1
fi

# 复用库里已有 cover 名称（通常静态文件目录已有这些图）
COVERS=(
  "b13e4d1a-cf82-4677-b641-bd3ea70c28fe.jpeg"
  "0cc1825b-a0f9-4919-a562-848298ee3beb.jpeg"
  "52ed401b-8e30-46d1-b8dc-1cbb981b1d25.jpeg"
  "2f459c21-444e-44d7-90f1-6483c63cd08f.jpeg"
  "380cf845-acc4-432b-b380-f0a9fcf99178.jpeg"
  "fdf04a22-8860-4073-9b8f-468777384a9b.jpeg"
  "426884a8-e390-47b3-a987-01baf06a1a25.jpeg"
  "06cde269-307c-420e-bf6c-7e48c2aed9ec.jpeg"
  "8acfe5ed-3f6e-4b7d-8a82-a333dcced753.png"
)

CLASS_IDS=(1 2 3 15 16)
PINZHONGS=("鲁南1号" "甘肃2号" "广西2号" "黑龙江三号" "乌鲁木齐99号")
DESC="瀑布流测试数据：用于验证图片错综布局与无限滚动加载。"

NOW_MS=$(date +%s%3N)
TMP_SQL=$(mktemp)

{
  echo "START TRANSACTION;"
  for ((i=1; i<=COUNT; i++)); do
    cover="${COVERS[$(( (i-1) % ${#COVERS[@]} ))]}"
    cid="${CLASS_IDS[$(( (i-1) % ${#CLASS_IDS[@]} ))]}"
    pinzhong="${PINZHONGS[$(( (i-1) % ${#PINZHONGS[@]} ))]}"

    price=$(( 9 + (i % 30) ))
    repertory=$(( 20 + (i * 3 % 120) ))
    pv=$(( i * 2 ))
    rate=$(( 3 + (i % 2) ))
    create_time=$(( NOW_MS + i ))

    title="${PREFIX}$(printf '%03d' "$i")"
    baozhiqi="$((1 + (i % 5)))年"
    shengchanriqi="2026年3月11日"

    title_esc=$(sql_escape "$title")
    cover_esc=$(sql_escape "$cover")
    desc_esc=$(sql_escape "$DESC")
    pinzhong_esc=$(sql_escape "$pinzhong")
    baozhiqi_esc=$(sql_escape "$baozhiqi")
    shengchanriqi_esc=$(sql_escape "$shengchanriqi")

    # status=0: 上架（前端会显示）
    cat <<SQL
INSERT INTO b_thing (
  title, cover, description, price, status, score,
  pinzhong, baozhiqi, shengchanriqi, repertory,
  create_time, rate, pv, recommend_count, wish_count, collect_count,
  classification_id, user_id
) VALUES (
  '${title_esc}', '${cover_esc}', '${desc_esc}', '${price}', '0', 0,
  '${pinzhong_esc}', '${baozhiqi_esc}', '${shengchanriqi_esc}', ${repertory},
  '${create_time}', ${rate}, ${pv}, 0, 0, 0,
  ${cid}, NULL
);
SQL
  done
  echo "COMMIT;"
} > "$TMP_SQL"

echo "[INFO] 正在写入 $COUNT 条测试商品..."
"${MYSQL_CMD[@]}" < "$TMP_SQL"
rm -f "$TMP_SQL"

echo "[OK] 插入完成。"
"${MYSQL_CMD[@]}" -e "SELECT COUNT(*) AS inserted_rows FROM b_thing WHERE title LIKE '${PREFIX_ESCAPED}%';"
echo "[TIP] 若需清理：bash doc/seed_things_for_waterfall.sh --cleanup"
