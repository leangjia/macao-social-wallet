export PATH := $(PATH):restdown/bin

all: api gossip

# dot: dot/freecoin_nxt.dot dot/freecoin_secretshare.dot
# 	cd .. && lein gossip && cd -

# png: freecoin_nxt.png freecoin_secretshare.png
# 	dot -Tpng $<

api:
	restdown API.md -m .

gossip:
	cd dot && ./render.sh && cd -

upload: api
	rsync -Pr API.html dyne@web-us.dyne.org:webapps/freecoin/api/index.html
