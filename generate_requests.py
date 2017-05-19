#!/usr/bin/python3
import sys
import random
import threading
import urllib.request
import time

def downloadFile(ip, port, f, sc, sr, wc, wr, coff = "0", roff = "0"):
	urllib.request.urlopen("http://" + ip + ":" + port + "/r.html?f=" + f + "&sc=" + sc + "&sr=" + sr + "&wc=" + wc + "&wr=" + wr + "&coff=" + coff + "&roff=" + roff).read()    

def generate_request():
	models = ("test01.txt", "test02.txt", "test03.txt", "test04.txt", "test05.txt", "test-texmap.txt", "wood.txt")
	model = random.choice(models)

	sc = random.randint(1000, 3000)
	sr = random.randint(1000, 3000)

	wc = sc
	wr = sr
	if(random.random() < 0.4):
		wc = random.randint(100, sc)
		wr = random.randint(100, sr)

	coff = 0
	roff = 0
	if(random.random() < 0.15):
		coff = random.randint(20, wc)
		roff = random.randint(20, wr)
		
	print((sys.argv[1], "8000", model, str(sc), str(sr), str(wc), str(wr), str(coff), str(roff)))

	threading.Thread(target=downloadFile, args=(sys.argv[1], "8000", model, str(sc), str(sr), str(wc), str(wr), str(coff), str(roff))).start()

def main():
	if(len(sys.argv) < 2):
		print("Vai tu (falta o ip xD)")
		exit(-1)

	while(True):
		generate_request()
		time.sleep(3)
    
if __name__ == "__main__":
	main()