from pymilvus import connections, utility, Collection

def check_milvus():
    try:
        # 连接到 Milvus
        connections.connect("default", host="localhost", port="19530")
        print("✅ 已连接到 Milvus")

        collection_name = "face_embeddings" # 匹配 application.yml
        
        # 检查所有集合
        collections = utility.list_collections()
        print(f"📋 现有集合: {collections}")

        if not utility.has_collection(collection_name):
            print(f"❌ 集合 '{collection_name}' 不存在")
            return

        # 获取集合信息
        collection = Collection(collection_name)
        collection.load() # 加载到内存
        
        num_entities = collection.num_entities
        print(f"📊 集合 '{collection_name}' 中的实体数量: {num_entities}")

        if num_entities > 0:
            print("🔍 预览前 5 条数据:")
            res = collection.query(expr="face_id != ''", output_fields=["face_id"], limit=5)
            for item in res:
                print(f" - ID: {item['face_id']}")
        else:
            print("⚠️ 集合目前为空。")

    except Exception as e:
        print(f"❌ 检查失败: {e}")

if __name__ == "__main__":
    check_milvus()
