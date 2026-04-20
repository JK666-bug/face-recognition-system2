"""
后端推送模块 (RabbitMQ 生产者)
负责将提取到的人脸数据异步发送给 SpringBoot 后端
"""

import pika
import os
import json
import base64
import cv2
import numpy as np
from datetime import datetime

class BackendPusher:
    def __init__(self, host='localhost', queue_name='face_recognition_queue', port=5888):
        self.host = host
        self.port = port
        self.queue_name = queue_name
        self.connection = None
        self.channel = None
        
    def _connect(self):
        """建立连接"""
        try:
            if self.connection is None or self.connection.is_closed:
                self.connection = pika.BlockingConnection(pika.ConnectionParameters(host=self.host, port=self.port))
                self.channel = self.connection.channel()
                self.channel.queue_declare(queue=self.queue_name, durable=True)
            return True
        except Exception as e:
            print(f"❌  无法连接到消息队列 (RabbitMQ): {e}")
            return False

    def push_face_data(self, vector, face_img, location="Unknown", video_id=None, quality_score=None, capture_reason=None):
        """
        推送人脸数据到 MQ
        
        Args:
            vector: 512维特征向量
            face_img: 人脸图像 (numpy ndarray)
            location: 拍摄地点
            video_id: 关联视频 ID
        """
        if not self._connect():
            return False
            
        try:
            # 1. 图像转为 Base64
            _, buffer = cv2.imencode('.jpg', face_img)
            img_base64 = base64.b64encode(buffer).decode('utf-8')
            
            # 2. 构造消息体
            payload = {
                "type": "FACE",
                "vector": vector,
                "image_base64": img_base64,
                "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                "location": location,
                "video_id": video_id,
                "quality_score": quality_score,
                "capture_reason": capture_reason
            }
            
            # 3. 发送消息
            self.channel.basic_publish(
                exchange='',
                routing_key=self.queue_name,
                body=json.dumps(payload),
                properties=pika.BasicProperties(
                    delivery_mode=2,  # 消息持久化
                )
            )
            print(f"✉️  数据已推送到 MQ: {self.queue_name} (地点: {location})")
            return True
        except Exception as e:
            print(f"❌  推送数据失败: {e}")
            return False

    def push_video_data(self, video_path, video_id):
        """
        将整个视频文件通过 MQ 发送
        """
        if not self._connect() or not os.path.exists(video_path):
            return False
            
        try:
            print(f"📦 正在准备通过 MQ 传输视频文件 (Base64): {video_id}")
            with open(video_path, "rb") as f:
                video_base64 = base64.b64encode(f.read()).decode('utf-8')
            
            payload = {
                "type": "VIDEO",
                "video_id": video_id,
                "video_base64": video_base64,
                "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            }
            
            self.channel.basic_publish(
                exchange='',
                routing_key=self.queue_name,
                body=json.dumps(payload)
            )
            print(f"✅ 视频数据已推送至 MQ: {video_id} (大小: {len(video_base64)//1024} KB)")
            return True
        except Exception as e:
            print(f"❌ 推送视频数据失败: {e}")
            return False

    def close(self):
        """关闭连接"""
        if self.connection and self.connection.is_open:
            self.connection.close()

# 单例模式，方便全局调用
pusher = BackendPusher()
