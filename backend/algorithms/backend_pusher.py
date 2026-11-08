"""
RabbitMQ producer. Publishes face crops and playback video to Spring Boot.
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
        """Open a connection if one is not already open."""
        try:
            if self.connection is None or self.connection.is_closed:
                self.connection = pika.BlockingConnection(pika.ConnectionParameters(host=self.host, port=self.port))
                self.channel = self.connection.channel()
                self.channel.queue_declare(queue=self.queue_name, durable=True)
            return True
        except Exception as e:
            print(f"Cannot connect to RabbitMQ: {e}")
            return False

    def push_face_data(self, vector, face_img, location="Unknown", video_id=None, quality_score=None, capture_reason=None):
        """
        Publish one face to the queue.

        Args:
            vector: 512-d embedding
            face_img: face image as a numpy ndarray
            location: capture location
            video_id: related playback id
        """
        if not self._connect():
            return False
            
        try:
            _, buffer = cv2.imencode('.jpg', face_img)
            img_base64 = base64.b64encode(buffer).decode('utf-8')
            
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
            
            self.channel.basic_publish(
                exchange='',
                routing_key=self.queue_name,
                body=json.dumps(payload),
                properties=pika.BasicProperties(
                    delivery_mode=2,  # persistent message
                )
            )
            print(f"Published to {self.queue_name} (location: {location})")
            return True
        except Exception as e:
            print(f"Failed to publish face data: {e}")
            return False

    def push_video_data(self, video_path, video_id):
        """Send a whole video file through the queue."""
        if not self._connect() or not os.path.exists(video_path):
            return False
            
        try:
            print(f"Preparing Base64 video for the queue: {video_id}")
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
            print(f"Published video {video_id} ({len(video_base64)//1024} KB)")
            return True
        except Exception as e:
            print(f"Failed to publish video: {e}")
            return False

    def close(self):
        """Close the connection."""
        if self.connection and self.connection.is_open:
            self.connection.close()

# Shared instance for the capture process.
pusher = BackendPusher()
