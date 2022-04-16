import os

def format_bytes(size):
    """Converts a number into bytes, distinguishing its magnitude.

    Args:
        size (int) -- Number of bytes to be translated.

    Returns:
        touple(string,string) -- Number of the assigned magnitude and the magnitude.
    """
    # 2**10 = 1024
    power = 2**10
    n = 0
    power_labels = {0: 'B', 1: 'KB', 2: 'MB', 3: 'GB', 4: 'TB'}
    while size > power:
        size /= power
        n += 1

    return str(round(size, 2))+" " + power_labels[n]


def getTrafficNode(nodeDirectory):
    traffic = {"sent": 0, "received": 0}
    if os.path.exists(f'{nodeDirectory}/sent/'):
        traffic["sent"] = sum(os.path.getsize(f'{nodeDirectory}/sent/{f}')
                              for f in os.listdir(f'{nodeDirectory}/sent') if os.path.isfile(f'{nodeDirectory}/sent/{f}'))

    if os.path.exists(f'{nodeDirectory}/received/'):
        traffic["received"] = sum(os.path.getsize(f'{nodeDirectory}/received/{f}') for f in os.listdir(
            f'{nodeDirectory}/received') if os.path.isfile(f'{nodeDirectory}/received/{f}'))

    traffic["total"] = traffic["sent"]+traffic["received"]

    return traffic
