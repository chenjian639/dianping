import random
import os

keywords = ["烧烤", "川菜", "火锅", "日料", "烤肉", "咖啡", "甜品", "夜宵"]

output_path = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "restaurant_test_data.sql"
)

with open(output_path, "w", encoding="utf-8") as f:
    batch = []
    for i in range(1, 100001):
        keyword = random.choice(keywords)
        name = f"测试{keyword}餐厅{i}"
        description = f"{keyword}特色餐厅，测试数据{i}"
        address = f"测试路{i}号"
        batch.append(f"('{name}','{description}','{address}',1)")

        if len(batch) == 500:
            sql = (
                    "INSERT INTO restaurant(name,description,address,status) VALUES\n"
                    + ",\n".join(batch)
                    + ";\n"
            )
            f.write(sql)
            batch = []

    # 处理最后不足 500 条的数据
    if batch:
        sql = (
                "INSERT INTO restaurant(name,description,address,status) VALUES\n"
                + ",\n".join(batch)
                + ";\n"
        )
        f.write(sql)