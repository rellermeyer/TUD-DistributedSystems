import sys
import random

f = open("services.txt", "w+")

class Coordinates:
    def __init__(self, lat, lon):
        self.lat = lat
        self.lon = lon

class Service:
    def __init__(self, name, url, c):
        self.name = name
        self.url = url
        self.coord = c

numServices = int(sys.argv[1])

# bottom left: 51.2412863967, 3.37153354
# bottom right: 51.2412863967, 7.1947757275
# top right: 53.6185799591, 7.1947757275 
# top left: 53.6185799591, 3.37153354

def generateCoord():
    lat = random.uniform(51.2412863967, 53.6185799591)
    lon = random.uniform(3.37153354, 7.1947757275)
    return Coordinates(lat, lon)

for i in range(numServices):
    c = generateCoord()
    name = i
    url = "https://google.com"
    s = "{}, {}, {}, {}\n".format(name, url, c.lat, c.lon)
    f.write(s)
