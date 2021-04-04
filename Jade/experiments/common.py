import requests

DEFAULT_PORT = 5000


def ping_all_nodes(ip_addresses, route='/'):
    node_availabilities = []

    for ip in ip_addresses:
        is_up = ping_url('http://' + ip + ':' + str(DEFAULT_PORT) + route)
        node_availabilities.append(is_up)

    return node_availabilities


def ping_url(url):
    try:
        request = requests.get(url, verify=False, timeout=1)
        if request.status_code == 200:
            return request.elapsed.microseconds / 1000
        else:
            print(request)
            return -1
    except Exception as e:
        print(e)
        return -1
