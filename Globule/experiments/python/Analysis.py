
# coding: utf-8

# In[1]:


import signal
import time, sys
import matplotlib.pyplot as plt
import requests
import wget
import csv
import mechanicalsoup
import time
from concurrent.futures import ThreadPoolExecutor
import urllib
import time
import numpy as np


# In[2]:


def get_image_urls_from_doc_url(url):
    browser = mechanicalsoup.StatefulBrowser()
    browser.open(url)
    images_on_page = browser.get_current_page().find_all("img")
    image_urls = []
    for image in images_on_page:
        image_url = image['src']
        if '//' not in image_url:
            if image_url[0] == '/':
                image_url = base + image_url
            else:
                image_url = base + '/' + image_url
        image_urls.append(image_url)
    
    print(image_urls)

    return image_urls


def get_url(url):
    return requests.get(url)

def time_total_request(url):

    start = time.time()

    image_urls = get_image_urls_from_doc_url(url)

    pool = ThreadPoolExecutor(max_workers=6)
    result = pool.map(get_url,image_urls)

    print(list(result))
    end = time.time()

    print("Time: ", end - start)

    return (end - start)

def time_to_first_byte_images(url):

    image_urls = get_image_urls_from_doc_url(url)

    results_images = []

    for image_url in image_urls:
        results_images.append(measure_ttfb(image_url))

    return results_images

def measure_ttfb(url):
    opener = urllib.request.build_opener()
    request = urllib.request.Request(url)

    start = time.time()
    resp = opener.open(request)
    # read one byte
    resp.read(1)
    ttfb = time.time() - start
    # read the rest
    resp.read()
    ttlb = time.time() - start  

    download_time = ttlb - ttfb

    return [ttfb, ttlb, download_time]


# In[4]:

# NOTE: Make sure to change this ip address to the matching location of the (main) server running Globule
base = "http://35.196.124.225"
# NOTE: Make sure to change the files according to the files located on the to be tested Globule server
url = "/multi.html"

results = time_to_first_byte_images(base + url)


# In[9]:


results = []

for i in range(80):
    time.sleep(1)
    print(i)
    results.append(time_to_first_byte_images(base + url))


# In[7]:


print(time_total_request(base+url))

