#!/usr/bin/python

import os
import sys

try:
  os.mkdir("data/downloaded")
except:
  pass

alternateNames = open("data/downloaded/alternateNames.txt")
if len(sys.argv) == 2:
  print "building buildings for %s" % sys.argv[1]
  input = open("data/downloaded/%s.txt" % sys.argv[1])
else:
  print "building buildings for the whole world"
  input = open("data/downloaded/allCountries.txt")

if not os.path.exists('data/private'):
  os.mkdir("data/private")
if not os.path.exists('data/private/features'):
  os.mkdir("data/private/features")

output = open("data/private/features/buildings.txt", "w")

gidList = set()

for line in alternateNames:
  line.strip
  parts = line.split('\t')
  gid = parts[1]
  lang = parts[2]
  if lang == 'link':
    gidList.add(gid)

for line in input:
  parts = line.split('\t')
  if parts[0] in gidList and parts[6] == 'S':
    output.write(line)

