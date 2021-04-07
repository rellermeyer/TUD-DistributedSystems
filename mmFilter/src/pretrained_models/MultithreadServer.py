import socket
import cv2
import numpy as np
from collections import deque
import threading
import time


class MultithreadServer():
    '''
    The Server class of the python part of the edge server. The socket listens for incoming data of the edge devices
    '''
    def __init__(self, host, port):
        self.host = host
        self.port = port
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind((self.host, self.port))
        self.q = deque()
        self.window_size = 12

    def listen_to_client(self, client, address, q):
        print(f'Connected by {address}')
        client.send(b'Welcome to the edge server!')
        count = 0
        frames = []
        while True:
            size = int.from_bytes(client.recv(2), 'big')
            bytes_data = client.recv(size)
            if bytes_data:
                data = np.frombuffer(bytes_data, dtype=np.uint8)  # from byte to array
                frame = cv2.imdecode(data, cv2.IMREAD_COLOR)
                frame = np.transpose(frame, (2, 0, 1))
                frames.append(frame)
                count += 1
                if count % self.window_size == 0:
                    q.append(np.stack(frames, axis=0))
                    frames = []
                print(f'Received frame {count} from {address}.')
            else:
                print('Client disconnected...')
                break
        client.close()
        return False

    def listen(self, sleep_time=5):
        self.sock.listen(5)
        print('Edge server started, waiting for requests...')
        while True:
            client, address = self.sock.accept()
            client.settimeout(20)
            threading.Thread(target=self.listen_to_client, args=(client, address, self.q)).start()
            print("Server is waiting...")
