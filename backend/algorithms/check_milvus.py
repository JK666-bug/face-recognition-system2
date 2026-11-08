from pymilvus import connections, utility, Collection

def check_milvus():
    try:
        connections.connect("default", host="localhost", port="19530")
        print("Connected to Milvus")

        collection_name = "face_embeddings"  # matches application.yml
        
        collections = utility.list_collections()
        print(f"Collections: {collections}")

        if not utility.has_collection(collection_name):
            print(f"Collection '{collection_name}' does not exist")
            return

        collection = Collection(collection_name)
        collection.load()
        
        num_entities = collection.num_entities
        print(f"Entities in '{collection_name}': {num_entities}")

        if num_entities > 0:
            print("First 5 rows:")
            res = collection.query(expr="face_id != ''", output_fields=["face_id"], limit=5)
            for item in res:
                print(f" - ID: {item['face_id']}")
        else:
            print("The collection is empty.")

    except Exception as e:
        print(f"Check failed: {e}")

if __name__ == "__main__":
    check_milvus()
