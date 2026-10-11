#!/usr/bin/env python3
"""生成全量商品目录 manifest（10 个一级分类 / 40 个二级分类 / 120 个商品）。

用法：
  python tools/seed-catalog/make_manifest.py            # 产出 tools/seed-catalog/manifest.full.json
  商品图片 ID 由 photos.full.json 提供（{ "20001": [28947260, ...] }），缺失的商品会在控制台列出，
  补齐 photos.full.json 后重跑本脚本即可。

设计说明：
  - 价格/产地/关键词为人工整理的真实农产品口径（便于答辩时讲得出产地与季节）；
  - 规格、销量、采摘日期按分类模板自动生成，保证数据分布看起来自然；
  - kw 字段是给采集脚本在 Pexels 上搜图用的英文关键词。
"""
import json
import random
from datetime import date, timedelta
from pathlib import Path

HERE = Path(__file__).resolve().parent

# ---------------- 分类：(id, 名称, 父id, 排序, 图标名) ----------------
CATEGORIES = [
    (1001, "水果", 0, 100, "watermelon"),
    (1002, "蔬菜", 0, 90, "broccoli"),
    (1003, "粮油米面", 0, 80, "sheaf-of-rice"),
    (1004, "肉禽蛋奶", 0, 70, "cut-of-meat"),
    (1005, "水产海鲜", 0, 60, "fish"),
    (1006, "菌菇山珍", 0, 50, "mushroom"),
    (1007, "干果零食", 0, 40, "peanuts"),
    (1008, "茶饮冲调", 0, 30, "teapot"),
    (1009, "调味酱料", 0, 20, "salt"),
    (1010, "农家特产", 0, 10, "basket"),
    # 水果
    (1101, "柑橘类", 1001, 10, "tangerine"),
    (1102, "浆果类", 1001, 9, "strawberry"),
    (1103, "热带水果", 1001, 8, "mango"),
    (1104, "仁果核果", 1001, 7, "red-apple"),
    (1105, "瓜果类", 1001, 6, "melon"),
    # 蔬菜
    (1111, "叶菜类", 1002, 10, "leafy-green"),
    (1112, "根茎类", 1002, 9, "carrot"),
    (1113, "茄果类", 1002, 8, "tomato"),
    (1114, "鲜食豆谷", 1002, 7, "beans"),
    (1115, "葱蒜类", 1002, 6, "onion"),
    # 粮油米面
    (1121, "大米", 1003, 10, "cooked-rice"),
    (1122, "杂粮", 1003, 9, "bowl-with-spoon"),
    (1123, "面粉挂面", 1003, 8, "spaghetti"),
    (1124, "食用油", 1003, 7, "oil-drum"),
    # 肉禽蛋奶
    (1131, "猪肉", 1004, 10, "pig-face"),
    (1132, "牛羊肉", 1004, 9, "meat-on-bone"),
    (1133, "禽类", 1004, 8, "poultry-leg"),
    (1134, "蛋类", 1004, 7, "egg"),
    (1135, "奶制品", 1004, 6, "glass-of-milk"),
    # 水产海鲜
    (1141, "淡水鱼虾", 1005, 10, "shrimp"),
    (1142, "海鱼", 1005, 9, "tropical-fish"),
    (1143, "虾蟹", 1005, 8, "crab"),
    (1144, "贝类", 1005, 7, "oyster"),
    # 菌菇山珍
    (1151, "鲜菇", 1006, 10, "brown-mushroom"),
    (1152, "干制菌菇", 1006, 9, "mushroom"),
    (1153, "竹笋", 1006, 8, "seedling"),
    (1154, "山珍干货", 1006, 7, "chestnut"),
    # 干果零食
    (1161, "坚果炒货", 1007, 10, "coconut"),
    (1162, "蜜饯果干", 1007, 9, "grapes"),
    (1163, "粗粮零食", 1007, 8, "rice-cracker"),
    # 茶饮冲调
    (1171, "绿茶红茶", 1008, 10, "teacup-without-handle"),
    (1172, "乌龙普洱", 1008, 9, "hot-beverage"),
    (1173, "花草茶", 1008, 8, "herb"),
    (1174, "蜂蜜冲调", 1008, 7, "bubble-tea"),
    # 调味酱料
    (1181, "香辛料", 1009, 10, "hot-pepper"),
    (1182, "基础调味", 1009, 9, "jar"),
    (1183, "酱料", 1009, 8, "honey-pot"),
    (1184, "腌菜泡菜", 1009, 7, "canned-food"),
    # 农家特产
    (1191, "腊味火腿", 1010, 10, "bacon"),
    (1192, "豆制品", 1010, 9, "beans"),
    (1193, "粉条干货", 1010, 8, "steaming-bowl"),
    (1194, "滋补特产", 1010, 7, "lotus"),
]

# ---------------- 商品：(二级分类, 名称, 售价, 产地, 搜图英文关键词[, 副标题]) ----------------
PRODUCTS = [
    # 水果 - 柑橘类
    (1101, "赣南脐橙 新鲜当季", 39.9, "江西赣州", "navel oranges whole", "产地直发 坏果包赔"),
    (1101, "武鸣沃柑 薄皮蜜柑", 25.9, "广西南宁", "mandarin oranges pile"),
    (1101, "秭归夏橙 现摘直发", 29.9, "湖北宜昌", "orange fruit tree fresh"),
    (1101, "福建琯溪蜜柚 三红肉", 32.9, "福建漳州", "red pomelo cut"),
    (1101, "象山红美人 柑橘", 68.0, "浙江宁波", "citrus fruit peeled fresh"),
    # 水果 - 浆果类
    (1102, "丹东红颜草莓", 49.9, "辽宁丹东", "fresh strawberries bowl", "冷链直发 头茬鲜果"),
    (1102, "云南高山蓝莓", 59.9, "云南昆明", "blueberries close up", "大果带白霜 现摘现发"),
    (1102, "烟台大樱桃 美早", 88.0, "山东烟台", "ripe cherries bunch"),
    (1102, "延边树莓 红树梅", 46.0, "吉林延边", "red raspberries"),
    (1102, "桑葚 现摘紫果", 36.0, "广东中山", "black mulberries"),
    (1102, "黑莓 新鲜果", 42.0, "贵州六盘水", "blackberries fresh"),
    # 水果 - 热带水果
    (1103, "海南贵妃芒", 32.9, "海南三亚", "ripe yellow mangoes", "树上熟 核小肉厚"),
    (1103, "广西小米蕉", 15.9, "广西崇左", "ripe bananas bunch", "自然成熟 甜糯无涩"),
    (1103, "泰国金枕头榴莲", 168.0, "海南乐东进口", "durian fruit opened"),
    (1103, "徐闻菠萝", 22.9, "广东湛江", "pineapple whole fresh"),
    (1103, "红心火龙果", 29.9, "广西南宁", "red dragon fruit cut"),
    (1103, "攀枝花凯特芒", 39.9, "四川攀枝花", "mango sliced plate"),
    (1103, "海南椰子 现摘", 26.0, "海南文昌", "fresh coconuts"),
    (1103, "三亚莲雾", 45.0, "海南三亚", "wax apples fruit"),
    # 水果 - 仁果核果
    (1104, "烟台红富士苹果", 34.9, "山东烟台", "red apples stack", "脆甜多汁 果径80+"),
    (1104, "陕西洛川红富士", 36.9, "陕西延安", "apples orchard fresh"),
    (1104, "赵县雪梨", 24.9, "河北石家庄", "pear fruit white"),
    (1104, "砀山酥梨", 21.9, "安徽宿州", "china pear fruit"),
    (1104, "库尔勒香梨", 39.9, "新疆巴州", "green pear fruit"),
    (1104, "奉化水蜜桃", 58.0, "浙江宁波", "ripe peach fruit"),
    (1104, "阳山水蜜桃", 66.0, "江苏无锡", "peach close up fresh"),
    # 水果 - 瓜果类
    (1105, "宁夏硒砂瓜", 26.9, "宁夏中卫", "watermelon sliced red"),
    (1105, "北京大兴西瓜", 32.0, "北京大兴", "watermelon whole field"),
    (1105, "海南哈密瓜", 45.0, "海南东方", "cantaloupe melon cut"),
    (1105, "羊角蜜甜瓜", 28.0, "山东潍坊", "melon green fresh"),
    # 蔬菜 - 叶菜类
    (1111, "有机西兰花 新鲜蔬菜", 12.9, "江西赣州", "broccoli green fresh", "无农药残留 基地直供"),
    (1111, "云南上海青", 6.9, "云南曲靖", "bok choy greens"),
    (1111, "本地菠菜", 8.9, "山东寿光", "fresh spinach leaves"),
    (1111, "球生菜", 7.9, "河北张家口", "lettuce head green"),
    (1111, "油麦菜", 6.5, "广东广州", "romaine lettuce leaves"),
    (1111, "大白菜", 9.9, "河南周口", "chinese cabbage"),
    (1111, "娃娃菜 三包", 11.9, "甘肃兰州", "baby napa cabbage"),
    # 蔬菜 - 根茎类
    (1112, "黄心土豆", 9.9, "甘肃定西", "potatoes raw pile"),
    (1112, "铁棍山药", 25.9, "河南焦作", "chinese yam root"),
    (1112, "水果胡萝卜", 12.9, "山东寿光", "fresh carrots bunch"),
    (1112, "沙地白萝卜", 8.9, "湖北武汉", "white radish daikon"),
    (1112, "洪湖九孔莲藕", 18.9, "湖北荆州", "lotus root fresh"),
    (1112, "烟薯25 蜜薯", 15.9, "山东临沂", "sweet potatoes orange"),
    (1112, "紫薯", 13.9, "广东湛江", "purple sweet potato"),
    (1112, "小黄姜", 10.9, "山东莱芜", "fresh ginger root"),
    # 蔬菜 - 茄果类
    (1113, "普罗旺斯西红柿", 16.9, "山东寿光", "ripe tomatoes vine"),
    (1113, "螺丝椒", 9.9, "河南商丘", "green chili peppers"),
    (1113, "紫长茄", 8.9, "江苏徐州", "eggplant purple fresh"),
    (1113, "密刺黄瓜", 7.9, "山东潍坊", "cucumbers fresh green"),
    (1113, "肉丝瓜", 10.9, "广东佛山", "luffa gourd vegetable"),
    (1113, "黑皮冬瓜", 12.9, "广西玉林", "wax gourd winter melon"),
    (1113, "圆茄子", 8.5, "河北保定", "round eggplant"),
    # 蔬菜 - 鲜食豆谷
    (1114, "带壳毛豆", 9.9, "江苏宿迁", "edamame green pods"),
    (1114, "荷兰豆", 11.9, "云南昆明", "snow peas pod"),
    (1114, "四季豆", 8.9, "四川成都", "green beans fresh"),
    (1114, "水果玉米 现摘", 15.9, "云南西双版纳", "sweet corn cobs"),
    (1114, "白不老豆角", 9.5, "陕西西安", "white beans vegetable"),
    # 蔬菜 - 葱蒜类
    (1115, "紫皮大蒜", 12.9, "山东金乡", "garlic bulbs purple"),
    (1115, "圆葱 紫皮", 7.9, "内蒙古乌兰察布", "red onions pile"),
    (1115, "小香葱", 4.9, "江苏南通", "spring onions scallion"),
    (1115, "章丘大葱", 13.9, "山东济南", "leek green onion long"),
    # 粮油米面 - 大米
    (1121, "五常大米 东北稻花香", 68.0, "黑龙江五常", "rice grains raw bowl", "粒粒晶莹 软糯香甜"),
    (1121, "盘锦蟹田大米", 55.0, "辽宁盘锦", "white rice sack"),
    (1121, "江苏射阳大米", 49.9, "江苏盐城", "rice field harvest"),
    (1121, "丝苗米 南方籼米", 42.0, "广东广州", "basmati rice grains"),
    (1121, "胚芽米 轻碾", 58.0, "吉林通化", "brown rice grains"),
    # 粮油米面 - 杂粮
    (1122, "沁州黄小米", 26.9, "山西长治", "millet grains yellow"),
    (1122, "红小豆", 15.9, "黑龙江牡丹江", "red adzuki beans"),
    (1122, "绿小豆", 14.9, "吉林白城", "mung beans green"),
    (1122, "黑米", 18.9, "陕西汉中", "black rice grains"),
    (1122, "燕麦米", 22.9, "内蒙古阴山", "oat grains"),
    (1122, "高粱米", 16.9, "辽宁朝阳", "sorghum grains"),
    # 粮油米面 - 面粉挂面
    (1123, "河套雪花粉", 32.9, "内蒙古巴彦淖尔", "flour bag wheat"),
    (1123, "中筋面粉 5kg", 27.9, "河北石家庄", "wheat flour bowl"),
    (1123, "手工空心挂面", 19.9, "陕西韩城", "dried noodles bundle"),
    (1123, "荞麦面", 24.9, "内蒙古乌兰察布", "buckwheat noodles"),
    # 粮油米面 - 食用油
    (1124, "小榨菜籽油 5L", 79.9, "四川成都", "cooking oil bottle rapeseed"),
    (1124, "胡姬花古法花生油", 128.0, "山东青岛", "peanut oil bottle"),
    (1124, "赣南山茶油", 168.0, "江西赣州", "camellia oil bottle"),
    (1124, "小磨香油", 29.9, "河北崔庙", "sesame oil bottle"),
    (1124, "玉米胚芽油", 65.0, "吉林松原", "corn oil bottle"),
    # 肉禽蛋奶 - 猪肉
    (1131, "土猪五花肉", 32.9, "江西赣州", "raw pork belly"),
    (1131, "黑猪里脊", 38.0, "湖南湘潭", "raw pork loin"),
    (1131, "猪筒骨 熬汤", 26.9, "河南驻马店", "pork bones soup"),
    (1131, "前腿肉 馅用", 28.9, "山东临沂", "minced pork"),
    # 肉禽蛋奶 - 牛羊肉
    (1132, "黄牛牛腩块", 55.9, "内蒙古通辽", "raw beef brisket"),
    (1132, "金钱腱子", 68.0, "宁夏固原", "beef shank raw"),
    (1132, "羔羊肉卷", 59.9, "内蒙古锡林郭勒", "lamb slices frozen"),
    (1132, "羊排段", 72.0, "新疆阿勒泰", "lamb ribs raw"),
    # 肉禽蛋奶 - 禽类
    (1133, "三黄鸡 整只", 39.9, "广东清远", "whole chicken raw"),
    (1133, "散养老母鸡", 58.0, "安徽宿州", "hen chicken farm"),
    (1133, "麻鸭 现杀", 46.0, "江苏高邮", "raw duck meat"),
    (1133, "鸡胸肉 低脂", 29.9, "山东潍坊", "chicken breast raw"),
    (1133, "鹅腿", 62.0, "广东汕头", "goose leg meat"),
    # 肉禽蛋奶 - 蛋类
    (1134, "土鸡蛋 散养柴鸡蛋", 28.0, "江西赣州", "brown eggs basket", "农家散养 营养丰富"),
    (1134, "麻鸭蛋", 24.0, "湖北仙桃", "duck eggs green"),
    (1134, "鹌鹑蛋", 15.9, "山东临沂", "quail eggs small"),
    (1134, "高邮咸鸭蛋", 33.9, "江苏扬州", "salted duck egg cut"),
    # 肉禽蛋奶 - 奶制品
    (1135, "摩拉水牛奶", 36.0, "广西柳州", "milk bottle glass"),
    (1135, "巴氏鲜牛奶", 18.9, "河北石家庄", "fresh milk jug"),
    (1135, "手工老酸奶", 22.9, "内蒙古呼和浩特", "yogurt bowl homemade"),
    # 水产海鲜 - 淡水鱼虾
    (1141, "现杀草鱼", 22.9, "湖北洪湖", "grass carp fish raw"),
    (1141, "土鲫鱼", 26.9, "江苏太湖", "crucian carp fish"),
    (1141, "鳜鱼 臭鳜鱼原料", 68.0, "安徽黄山", "mandarin fish"),
    (1141, "潜江小龙虾", 59.9, "湖北潜江", "crayfish red cooked"),
    # 水产海鲜 - 海鱼
    (1142, "宁德大黄鱼", 45.9, "福建宁德", "yellow croaker fish"),
    (1142, "舟山带鱼中段", 39.9, "浙江舟山", "belt fish hairtail"),
    (1142, "青岛鲅鱼", 33.9, "山东青岛", "mackerel fish fresh"),
    (1142, "阿拉斯加鳕鱼", 52.0, "进口冷链", "cod fish fillet"),
    # 水产海鲜 - 虾蟹
    (1143, "湛江基围虾", 49.9, "广东湛江", "fresh prawns shrimp"),
    (1143, "南美白虾 冻鲜", 58.0, "山东日照", "white shrimp frozen"),
    (1143, "阳澄湖大闸蟹", 168.0, "江苏苏州", "chinese mitten crab"),
    (1143, "梭子蟹", 66.0, "浙江宁波", "swimming crab"),
    # 水产海鲜 - 贝类
    (1144, "乳山生蚝", 39.9, "山东威海", "oysters on ice"),
    (1144, "花蛤 沙蛤", 21.9, "福建漳州", "clams shellfish"),
    (1144, "獐子岛扇贝", 45.0, "辽宁大连", "scallop shell"),
    (1144, "鲍鱼 鲜货", 78.0, "福建连江", "abalone fresh"),
    # 菌菇山珍 - 鲜菇
    (1151, "庆元香菇 鲜菇", 15.9, "浙江丽水", "fresh shiitake mushrooms"),
    (1151, "金针菇", 7.9, "江苏灌南", "enoki mushrooms"),
    (1151, "杏鲍菇", 12.9, "山东邹城", "king oyster mushroom"),
    (1151, "双孢白蘑菇", 16.9, "福建漳州", "white button mushrooms"),
    (1151, "平菇", 9.9, "河南西峡", "oyster mushrooms"),
    # 菌菇山珍 - 干制菌菇
    (1152, "椴木干香菇", 39.9, "湖北随州", "dried shiitake mushrooms"),
    (1152, "东北黑木耳", 29.9, "黑龙江东宁", "dried wood ear fungus"),
    (1152, "古田银耳", 26.9, "福建宁德", "white snow fungus"),
    (1152, "羊肚菌", 128.0, "云南香格里拉", "morel mushrooms"),
    (1152, "干茶树菇", 24.9, "江西广昌", "dried tea tree mushroom"),
    # 菌菇山珍 - 竹笋
    (1153, "天目山雷笋", 28.9, "浙江杭州", "bamboo shoots fresh"),
    (1153, "冬笋", 36.0, "江西宜春", "winter bamboo shoot"),
    (1153, "笋干 扁尖", 42.0, "安徽黄山", "dried bamboo shoots"),
    (1153, "手剥笋", 25.9, "福建三明", "pickled bamboo shoots"),
    # 菌菇山珍 - 山珍干货
    (1154, "迁西板栗", 26.9, "河北唐山", "chestnuts raw"),
    (1154, "长白山松子", 68.0, "吉林白山", "pine nuts"),
    (1154, "野生蕨菜干", 22.9, "湖南湘西", "dried fern vegetable"),
    (1154, "昆仑雪菊", 39.9, "新疆和田", "chrysanthemum flowers dried"),
    # 干果零食 - 坚果炒货
    (1161, "新疆纸皮核桃", 39.9, "新疆阿克苏", "walnuts in shell"),
    (1161, "东北松子 手剥", 78.0, "黑龙江伊春", "pine nuts shelled"),
    (1161, "巴旦木 原味", 42.0, "新疆喀什", "almonds raw"),
    (1161, "夏威夷果", 56.0, "云南临沧", "macadamia nuts"),
    (1161, "临安山核桃", 68.0, "浙江杭州", "hickory nuts"),
    (1161, "带壳花生 晒干", 15.9, "河南南阳", "peanuts in shell"),
    # 干果零食 - 蜜饯果干
    (1162, "吐鲁番葡萄干", 29.9, "新疆吐鲁番", "raisins dried grapes"),
    (1162, "若羌灰枣", 34.9, "新疆巴州", "dried jujubes red dates"),
    (1162, "芒果干 无添加", 26.9, "广西百色", "dried mango slices"),
    (1162, "富平柿饼", 39.9, "陕西渭南", "dried persimmons"),
    (1162, "无花果干", 45.0, "新疆阿图什", "dried figs"),
    (1162, "山楂片 空心", 18.9, "山东青州", "dried hawthorn slices"),
    # 干果零食 - 粗粮零食
    (1163, "倒蒸红薯干", 22.9, "福建连城", "dried sweet potato strips"),
    (1163, "手工米饼", 19.9, "江西萍乡", "rice cakes crackers"),
    (1163, "锅巴 小米", 16.9, "安徽宣城", "rice crust snack"),
    (1163, "芝麻糖", 21.9, "山东菏泽", "sesame seed candy"),
    # 茶饮冲调 - 绿茶红茶
    (1171, "明前西湖龙井", 168.0, "浙江杭州", "green tea leaves dry"),
    (1171, "黄山毛峰", 88.0, "安徽黄山", "china green tea"),
    (1171, "祁门红茶", 98.0, "安徽黄山", "black tea leaves"),
    (1171, "信阳毛尖", 79.0, "河南信阳", "green tea cup brew"),
    # 茶饮冲调 - 乌龙普洱
    (1172, "安溪铁观音", 118.0, "福建泉州", "oolong tea leaves"),
    (1172, "武夷山大红袍", 158.0, "福建南平", "rock tea oolong"),
    (1172, "云南普洱熟茶饼", 128.0, "云南普洱", "puer tea cake"),
    (1172, "福鼎白茶 白牡丹", 96.0, "福建宁德", "white tea leaves"),
    # 茶饮冲调 - 花草茶
    (1173, "杭白菊 胎菊", 32.9, "浙江桐乡", "chamomile flowers tea"),
    (1173, "平阴玫瑰花茶", 39.9, "山东济南", "dried rose buds"),
    (1173, "金银花", 36.0, "山东平邑", "honeysuckle flowers dried"),
    (1173, "茉莉花茶", 58.0, "广西横州", "jasmine tea flowers"),
    # 茶饮冲调 - 蜂蜜冲调
    (1174, "洋槐蜂蜜", 49.9, "河南三门峡", "honey jar golden"),
    (1174, "枣花蜜", 42.0, "山西运城", "honey dipper wooden"),
    (1174, "深山土蜂蜜", 88.0, "陕西汉中", "raw honey comb"),
    (1174, "纯藕粉", 29.9, "湖北武汉", "lotus root starch powder"),
    (1174, "黑芝麻糊", 26.9, "江西宜春", "black sesame powder"),
    # 调味酱料 - 香辛料
    (1181, "富宁八角", 19.9, "云南文山", "star anise spice"),
    (1181, "汉源花椒", 32.9, "四川雅安", "sichuan peppercorns"),
    (1181, "线椒干 辣椒段", 15.9, "新疆塔城", "dried red chilies"),
    (1181, "桂皮 香叶组合", 18.9, "广西防城港", "cinnamon sticks spices"),
    (1181, "白胡椒粉", 14.9, "海南万宁", "white pepper powder"),
    # 调味酱料 - 基础调味
    (1182, "土冰糖 黄冰糖", 18.9, "云南西双版纳", "rock sugar candy"),
    (1182, "古法红糖", 22.9, "广西贵港", "brown sugar blocks"),
    (1182, "井盐 未加碘", 9.9, "四川自贡", "salt crystals coarse"),
    (1182, "纯粮酿造醋", 16.9, "山西清徐", "vinegar bottle brewing"),
    (1182, "头抽酱油", 24.9, "广东佛山", "soy sauce bottle"),
    # 调味酱料 - 酱料
    (1183, "郫县豆瓣酱", 19.9, "四川成都", "doubanjiang chili paste"),
    (1183, "东北黄豆酱", 16.9, "辽宁锦州", "soybean paste jar"),
    (1183, "蒜蓉辣酱", 14.9, "山东临沂", "garlic chili sauce"),
    (1183, "芝麻酱", 26.9, "河北崔庙", "sesame paste jar"),
    # 调味酱料 - 腌菜泡菜
    (1184, "涪陵榨菜芯", 12.9, "重庆涪陵", "pickled mustard tuber"),
    (1184, "酸豆角", 13.9, "湖南长沙", "pickled green beans"),
    (1184, "萝卜干", 11.9, "广东潮汕", "dried radish pickle"),
    (1184, "梅干菜", 17.9, "浙江绍兴", "dried preserved vegetables"),
    # 农家特产 - 腊味火腿
    (1191, "湘西烟熏腊肉", 58.0, "湖南湘西", "chinese cured bacon", "传统工艺 手工制作"),
    (1191, "广式腊肠", 46.0, "广东东莞", "chinese sausage cured"),
    (1191, "南宁板鸭", 52.0, "广西桂林", "dried cured duck"),
    (1191, "金华火腿", 128.0, "浙江金华", "jinhua ham cured"),
    # 农家特产 - 豆制品
    (1192, "手工豆腐干", 18.9, "安徽八公山", "dried tofu slices"),
    (1192, "石磨腐竹", 24.9, "江西高安", "dried tofu skin yuba"),
    (1192, "千张 百叶", 12.9, "江苏苏州", "tofu sheets skin"),
    (1192, "油豆皮", 15.9, "山东济宁", "fried tofu skin"),
    # 农家特产 - 粉条干货
    (1193, "红薯粉条", 19.9, "河南禹州", "sweet potato glass noodles"),
    (1193, "土豆粉条", 17.9, "内蒙古乌兰察布", "potato noodles dry"),
    (1193, "桂林米粉", 21.9, "广西桂林", "rice noodles dried"),
    (1193, "莜面 燕麦面", 26.9, "内蒙古阴山", "oat flour noodles"),
    # 农家特产 - 滋补特产
    (1194, "宁夏枸杞 免洗", 39.9, "宁夏中宁", "goji berries wolfberry"),
    (1194, "黄精 九蒸九晒", 88.0, "安徽九华山", "polygonatum dried herb"),
    (1194, "长白山人参片", 118.0, "吉林抚松", "ginseng root slices"),
    (1194, "糯米酒 月子酒", 36.0, "江西赣州", "rice wine jar"),
]

# 分类 -> 副标题模板（未显式给副标题时使用）
SUBTITLE_BY_L1 = {
    1101: "产地直发 现摘现发", 1102: "冷链锁鲜 坏果包赔", 1103: "果园直供 甜度高",
    1104: "脆甜多汁 果径均匀", 1105: "当季鲜果 沙甜爽口",
    1111: "当日采摘 基地直供", 1112: "沙地种植 粉糯香甜", 1113: "大棚鲜蔬 现摘现发",
    1114: "鲜嫩饱满 清甜无渣", 1115: "辛香浓郁 耐储耐运",
    1121: "当季新米 轻碾保鲜", 1122: "颗粒饱满 农家自种", 1123: "石磨低速 麦香浓郁",
    1124: "物理压榨 非转基因",
    1131: "冷鲜排酸 现分割", 1132: "草饲散养 冷链直发", 1133: "农家散养 现杀现发",
    1134: "当日鲜蛋 破损包赔", 1135: "低温冷链 原生营养",
    1141: "活鲜现杀 冰袋发货", 1142: "船冻锁鲜 肉质紧实", 1143: "肥美当季 净重发货",
    1144: "深水养殖 鲜度可见",
    1151: "基地当日采菇", 1152: "自然晾晒 无硫熏", 1153: "深山现挖 脆嫩无渣",
    1154: "山野原生 手工挑选",
    1161: "当季新货 原色原味", 1162: "自然风干 无添加", 1163: "老灶手工 粗粮本味",
    1171: "核心产区 明前采摘", 1172: "传统工艺 岩骨花香", 1173: "花蕾初开 手工烘干",
    1174: "波美度足 无添加",
    1181: "原产地直采 香气浓", 1182: "纯粮酿造 零添加", 1183: "老缸发酵 酱香醇厚",
    1184: "传统腌渍 爽口下饭",
    1191: "柴火慢熏 老宅风味", 1192: "石磨卤水 手工压制", 1193: "无明矾 纯薯制",
    1194: "道地药材 手工甄选",
}

STORAGE_BY_L1 = {
    1101: "常温避光", 1102: "冷藏保存", 1103: "常温催熟", 1104: "冷藏保存", 1105: "常温避光",
    1111: "冷藏保湿", 1112: "阴凉干燥", 1113: "冷藏保存", 1114: "冷藏保湿", 1115: "阴凉通风",
    1121: "阴凉干燥", 1122: "阴凉干燥", 1123: "阴凉干燥", 1124: "避光常温",
    1131: "0-4℃冷藏", 1132: "-18℃冷冻", 1133: "0-4℃冷藏", 1134: "冷藏保存", 1135: "2-6℃冷藏",
    1141: "冰鲜 0℃", 1142: "-18℃冷冻", 1143: "-18℃冷冻", 1144: "冰鲜 0℃",
    1151: "冷藏保湿", 1152: "密封干燥", 1153: "冷藏保存", 1154: "密封干燥",
    1161: "密封防潮", 1162: "密封阴凉", 1163: "密封阴凉",
    1171: "避光防潮", 1172: "避光防潮", 1173: "避光防潮", 1174: "常温避光",
    1181: "密封防潮", 1182: "常温避光", 1183: "常温避光", 1184: "阴凉干燥",
    1191: "冷冻保存", 1192: "冷藏保存", 1193: "阴凉干燥", 1194: "密封阴凉",
}

SHELF_BY_L1 = {
    1101: "30天", 1102: "5天", 1103: "10天", 1104: "20天", 1105: "15天",
    1111: "5天", 1112: "30天", 1113: "7天", 1114: "7天", 1115: "45天",
    1121: "18个月", 1122: "12个月", 1123: "12个月", 1124: "18个月",
    1131: "7天", 1132: "180天", 1133: "5天", 1134: "15天", 1135: "15天",
    1141: "3天", 1142: "180天", 1143: "180天", 1144: "3天",
    1151: "7天", 1152: "12个月", 1153: "10天", 1154: "12个月",
    1161: "8个月", 1162: "9个月", 1163: "6个月",
    1171: "18个月", 1172: "24个月", 1173: "18个月", 1174: "24个月",
    1181: "18个月", 1182: "24个月", 1183: "12个月", 1184: "12个月",
    1191: "180天", 1192: "30天", 1193: "18个月", 1194: "12个月",
}

SPEC_NAMES = {
    1101: ["5斤装", "9斤装"], 1102: ["盒装 500g", "箱装 1000g"], 1103: ["5斤装", "9斤装"],
    1104: ["5斤装", "9斤装"], 1105: ["单果 5斤", "双果 10斤"],
    1111: ["500g", "1000g"], 1112: ["2.5斤", "5斤"], 1113: ["500g", "2斤"], 1114: ["500g", "1000g"],
    1115: ["1斤", "5斤"],
    1121: ["5kg装", "10kg装"], 1122: ["1kg装", "2.5kg装"], 1123: ["2.5kg", "5kg"], 1124: ["1.8L", "5L"],
    1131: ["500g", "1000g"], 1132: ["500g", "1000g"], 1133: ["整只", "整只×2"], 1134: ["20枚", "40枚"],
    1135: ["950ml", "1900ml"],
    1141: ["约1000g", "约2000g"], 1142: ["500g", "1000g"], 1143: ["750g", "1500g"], 1144: ["10只", "24只"],
    1151: ["250g", "500g"], 1152: ["100g", "250g"], 1153: ["1000g", "2500g"], 1154: ["500g", "1000g"],
    1161: ["250g", "500g"], 1162: ["250g", "500g"], 1163: ["200g", "500g"],
    1171: ["50g", "100g"], 1172: ["50g", "饼 357g"], 1173: ["50g", "100g"], 1174: ["250g", "500g"],
    1181: ["50g", "100g"], 1182: ["250g", "500g"], 1183: ["230g", "450g"], 1184: ["200g", "500g"],
    1191: ["500g", "1000g"], 1192: ["250g", "500g"], 1193: ["500g", "1000g"], 1194: ["100g", "250g"],
}


def build() -> dict:
    rnd = random.Random(20261011)
    photos = {}
    pf = HERE / "photos.full.json"
    if pf.exists():
        photos = {str(k): v for k, v in json.loads(pf.read_text(encoding="utf-8")).items()}

    categories = [
        {"id": cid, "name": name, "parent_id": parent, "sort": sort, "icon": icon}
        for cid, name, parent, sort, icon in CATEGORIES
    ]
    products = []
    missing = []
    seq: dict[int, int] = {}
    for idx, row in enumerate(PRODUCTS):
        cat, name, price, origin, kw = row[0], row[1], row[2], row[3], row[4]
        seq[cat] = seq.get(cat, 0) + 1
        subtitle = row[5] if len(row) > 5 else SUBTITLE_BY_L1.get(cat, "产地直发 新鲜直达")
        pid = 20001 + idx
        ids = photos.get(str(pid)) or photos.get(name)
        if not ids:
            missing.append((pid, name, kw))
        base = date(2026, 9, 1) + timedelta(days=rnd.randint(-25, 20))
        specs = []
        for si, sname in enumerate(SPEC_NAMES.get(cat, ["标准装", "家庭装"])):
            mult = 1.0 if si == 0 else round(rnd.uniform(1.7, 1.95), 2)
            specs.append(
                {
                    "name": sname,
                    "price": round(price * mult, 1),
                    "stock": rnd.choice([80, 120, 150, 200, 260, 320]),
                }
            )
        products.append(
            {
                "id": pid,
                "name": name,
                "subtitle": subtitle,
                "category_id": cat,
                "price": price,
                "original_price": round(price * round(rnd.uniform(1.25, 1.6), 2), 1),
                "stock": rnd.choice([120, 180, 240, 300, 420, 500]),
                "sales": rnd.randint(60, 1600),
                "origin": origin,
                "harvest_date": base.isoformat(),
                "shelf_life": SHELF_BY_L1.get(cat, "12个月"),
                "storage": STORAGE_BY_L1.get(cat, "阴凉干燥"),
                "sort": 100 - 10 * seq[cat],
                "photos": ids or [],
                "kw": kw,
                "specs": specs,
            }
        )

    pid_of = {p["name"]: p["id"] for p in products}

    def pid_of_name(name: str) -> int:
        pid = pid_of.get(name)
        if pid is None:
            raise SystemExit(f"[ERR] manifest 引用了不存在的商品名：{name}")
        return pid

    def banner(bid: int, title: str, prod_name: str, sort: int, photo: int = None):
        pid = pid_of_name(prod_name)
        return {
            "id": bid,
            "title": title,
            "link": f"/pages/product/product?id={pid}",
            "sort": sort,
            "photo": photo or (products[pid - 20001]["photos"] or [None])[0],
        }

    banners = [
        banner(22001, "赣南脐橙 产地直发", "赣南脐橙 新鲜当季", 100, 28947260),
        banner(22002, "五常新米 轻碾保鲜", "五常大米 东北稻花香", 95),
        banner(22003, "海南贵妃芒 树上熟", "海南贵妃芒", 90, 16724963),
        banner(22004, "阳澄湖大闸蟹 开捕", "阳澄湖大闸蟹", 85),
        banner(22005, "明前龙井 核心产区", "明前西湖龙井", 80),
    ]

    def review(prod_name: str, rating: int, content: str, label: int, score: float, keywords: str):
        return {
            "product_id": pid_of_name(prod_name),
            "rating": rating,
            "content": content,
            "label": label,
            "score": score,
            "keywords": keywords,
        }

    ORANGE = "赣南脐橙 新鲜当季"
    STRAWBERRY = "丹东红颜草莓"
    MANGO = "海南贵妃芒"
    RICE = "五常大米 东北稻花香"
    BROCCOLI = "有机西兰花 新鲜蔬菜"
    EGG = "土鸡蛋 散养柴鸡蛋"
    BACON = "湘西烟熏腊肉"

    demo = {
        "reviews": [
            review(ORANGE, 5, "非常好吃的脐橙，新鲜多汁！", 1, 1.0, "新鲜,好吃,多汁,好"),
            review(ORANGE, 5, "回购多次了，个头均匀皮薄，日期新鲜。", 1, 1.0, "回购,新鲜,均匀"),
            review(ORANGE, 2, "有两三个偏酸，客服已按坏果赔付。", -1, 0.0, "偏酸"),
            review(STRAWBERRY, 5, "草莓很新鲜，包装用心，一颗没压坏。", 1, 1.0, "新鲜,用心"),
            review(STRAWBERRY, 1, "路上耽搁，到手有几颗发霉，失望。", -1, 0.0, "发霉,失望"),
            review(MANGO, 5, "贵妃芒核小肉厚，很甜，物流也快。", 1, 1.0, "好吃,甜,快"),
            review(RICE, 5, "五常大米真的很香，煮饭满屋飘香，正宗！", 1, 1.0, "正宗,香"),
            review(RICE, 3, "米还行，但外袋有点破损。", -1, 0.3, "破损,还行"),
            review(BROCCOLI, 5, "西兰花非常新鲜，口感脆嫩，会回购。", 1, 1.0, "新鲜,回购,脆,嫩"),
            review(BROCCOLI, 2, "有几朵发黄，不太新鲜。", -1, 0.0, "不新鲜,失望"),
            review(EGG, 5, "土鸡蛋蛋黄颜色深，炒出来很香。", 1, 1.0, "新鲜,香"),
            review(BACON, 5, "湘西腊肉烟熏味正，就是稍微咸了点。", 1, 1.0, "正宗,咸"),
        ],
        "traceability": [
            {
                "id": 2097480000000002001,
                "product_id": pid_of_name(ORANGE),
                "batch_no": "B202609110001",
                "farmer_name": "信丰脐橙种植合作社",
                "farmer_phone": "13800000000",
                "origin_province": "江西省",
                "origin_city": "赣州市",
                "origin_district": "信丰县",
                "planting_date": "2026-03-10",
                "harvest_date": "2026-09-08",
                "quality_check_result": "农残检测合格",
                "pesticide_test": "GB 2763全项合格",
                "certification_type": "GEOGRAPHICAL",
                "records": [
                    {"type": "SEEDING", "date": "2026-03-10", "content": "完成幼苗定植，株距 3m，覆盖地膜保温", "operator": "李四"},
                    {"type": "FERTILIZING", "date": "2026-05-20", "content": "施用腐熟有机肥，补充磷钾元素", "operator": "李四"},
                    {"type": "WATERING", "date": "2026-06-15", "content": "伏旱期滴灌补水两次", "operator": "李四"},
                    {"type": "PEST_CONTROL", "date": "2026-07-10", "content": "释放捕食螨生物防治，未使用化学农药", "operator": "李四"},
                    {"type": "HARVEST", "date": "2026-09-08", "content": "人工采摘，糖度抽检 12.8，分拣装箱", "operator": "李四"},
                ],
            },
            {
                "id": 2097480000000002002,
                "product_id": pid_of_name(BROCCOLI),
                "batch_no": "B202608220001",
                "farmer_name": "赣州绿源蔬菜种植合作社",
                "farmer_phone": "13800000001",
                "origin_province": "江西省",
                "origin_city": "赣州市",
                "origin_district": "章贡区",
                "planting_date": "2026-07-15",
                "harvest_date": "2026-08-22",
                "quality_check_result": "农残检测合格",
                "pesticide_test": "GB 2763全项合格",
                "certification_type": "ORGANIC",
                "records": [
                    {"type": "SEEDING", "date": "2026-07-15", "content": "穴盘育苗完成，移栽大田", "operator": "王五"},
                    {"type": "FERTILIZING", "date": "2026-07-30", "content": "追施水溶肥一次", "operator": "王五"},
                    {"type": "WATERING", "date": "2026-08-05", "content": "喷灌补水，保持土壤湿度", "operator": "王五"},
                    {"type": "PEST_CONTROL", "date": "2026-08-12", "content": "物理防虫（粘虫板），无农药施用", "operator": "王五"},
                    {"type": "HARVEST", "date": "2026-08-22", "content": "清晨人工采收，冷链预冷后发货", "operator": "王五"},
                ],
            },
        ],
        "seckill": [
            {"id": 2097480000000003001, "activity_code": "DEMOSK20261001", "product_id": pid_of_name(BROCCOLI), "seckill_price": 3.9, "seckill_stock": 50, "seckill_limit": 1, "sort": 50},
            {"id": 2097480000000003002, "activity_code": "DEMOSK20261002", "product_id": pid_of_name(ORANGE), "seckill_price": 19.9, "seckill_stock": 80, "seckill_limit": 2, "sort": 45},
            {"id": 2097480000000003003, "activity_code": "DEMOSK20261003", "product_id": pid_of_name(RICE), "seckill_price": 39.9, "seckill_stock": 60, "seckill_limit": 1, "sort": 40},
        ],
    }

    manifest = {
        "meta": {"icon_prefix": "fluent-emoji-flat", "icon_size": 160, "photo_size": 800},
        "categories": categories,
        "products": products,
        "banners": banners,
        "demo": demo,
    }
    return manifest, missing


if __name__ == "__main__":
    import sys

    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    manifest, missing = build()
    out = HERE / "manifest.full.json"
    out.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    l1 = [c for c in manifest["categories"] if c["parent_id"] == 0]
    l2 = [c for c in manifest["categories"] if c["parent_id"] != 0]
    print(f"[OK] {out.name}: 一级 {len(l1)} / 二级 {len(l2)} / 商品 {len(manifest['products'])} / 轮播 {len(manifest['banners'])}")
    if missing:
        print(f"[WARN] {len(missing)} 个商品还没有 Pexels 图片 ID（写入 photos.full.json 后重跑）")
        for pid, name, kw in missing[:5]:
            print(f"   e.g. {pid} {name}  kw={kw}")
    no_photo = [p["id"] for p in manifest["products"] if not p["photos"]]
    print(f"[INFO] 无图商品数 {len(no_photo)}")
