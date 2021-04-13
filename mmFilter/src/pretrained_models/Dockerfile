FROM python:3.6.8
COPY . /app
WORKDIR /app
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update -y
RUN apt install libgl1-mesa-glx -y
RUN apt-get install 'ffmpeg'\
    'libsm6'\
    'libxext6'  -y
RUN pip3 install --upgrade pip

RUN pip3 install opencv-python==4.3.0.38
RUN pip3 install numpy
RUN pip3 install torch==1.4.0 --no-cache-dir
RUN pip3 install transformers
EXPOSE 80

CMD ["/bin/sh"]