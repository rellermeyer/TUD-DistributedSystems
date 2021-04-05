from flask import Flask
from flask import request
from flask_sqlalchemy import SQLAlchemy
from flask import abort
import os

app = Flask(__name__)
USER = "postgres"
PASSWORD = "pass"
DB = "jade"


@app.route('/')
def index():
    return "Hello world!"


@app.route('/get-data')
def get_data():
    ip = os.environ['targetIp']
    port = os.environ['targetPort']
    try:
        address = ip+":"+port
        app.config['SQLALCHEMY_DATABASE_URI'] = f"postgresql+psycopg2://{USER}:{PASSWORD}@{address}/{DB}"
        db = SQLAlchemy(app)
        result_set = db.engine.execute("SELECT * FROM jade")

        results = []
        for r in result_set:
            results.append(r)
        return str(results)
    except BaseException as e:
        abort(500)


if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0')
