FROM python:3-onbuild
COPY . /usr/src/app
ENV FLASK_APP app.py
EXPOSE 5000/tcp
CMD ["flask", "run", "-h", "0.0.0.0"]
