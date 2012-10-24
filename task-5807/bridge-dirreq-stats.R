library(ggplot2)
library(reshape)
library(scales)

# Commented out, because this graph takes a while to draw...
#d <- read.csv("out/dirreq-responses", stringsAsFactors = FALSE,
#  header = FALSE)
#d <- data.frame(date = as.Date(d$V1), requests = d$V4,
#  asrelay = ifelse(d$V3, "also seen as\nnon-bridge relays",
#  "only seen as\nbridges"))
#ggplot(d, aes(x = date, y = requests)) +
#geom_point() +
#facet_grid(asrelay ~ .) +
#scale_x_date(name = "",
#  labels = date_format("%b %Y"),
#  minor_breaks = date_breaks("1 month")) +
#scale_y_continuous(name = "", labels = comma_format(digits = 1))
#ggsave("graphs/responses-single-bridges.png", width = 6, height = 3.5,
#  dpi = 100)

# ALTERNATIVE: out/bridge-dirreq-stats-no-relays
b <- read.csv("out/bridge-dirreq-stats-all-bridges",
  stringsAsFactors = FALSE)
b <- b[b$date >= "2011-07-01" & b$date <= "2012-09-30", ]

x <- data.frame(date = b$date,
  value = (b$ha * (b$na + b$nc) + (b$ha + b$hc) * b$nb) /
          ((b$ha + b$hc) * b$nabcd))
x <- melt(x, id = "date")
ggplot(x, aes(x = as.Date(date), y = value)) +
geom_line() +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  minor_breaks = date_breaks("1 month")) +
scale_y_continuous(name = "", limit = c(0, 1), labels = percent)
ggsave("graphs/fraction.pdf", width = 6, height = 3, dpi = 100)

ggplot(b, aes(x = as.Date(date), y = (ra + rb) / 86400)) +
geom_line() +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  minor_breaks = date_breaks("1 month")) +
scale_y_continuous(name = "", labels = comma_format(digits = 1))
ggsave("graphs/responses.pdf", width = 6, height = 3, dpi = 72)

x <- data.frame(
  date = as.Date(b$date),
  responses = (b$ra + b$rb) / 86400,
  fraction = (b$ha * (b$na + b$nc) + (b$ha + b$hc) * b$nb) /
    ((b$ha + b$hc) * b$nabcd),
  totalresponses = ((b$ra + b$rb) * (b$ha + b$hc) *
    b$nabcd) / (b$ha * (b$na + b$nc) + (b$ha + b$hc) * b$nb) / 86400)
x <- melt(x, id = "date")
x <- data.frame(date = x$date, value = x$value, variable =
  ifelse(x$variable == "responses",
    "1. Reported directory\nrequests",
  ifelse(x$variable == "fraction", paste("2. Estimated fraction\n",
    "of bridges reporting\ndirectory requests", sep = ""),
    "3. Estimated directory\nrequests in the\nnetwork")))
ggplot(x, aes(x = as.Date(date), y = value)) +
geom_line() +
facet_grid(variable ~ ., scales = "free_y") +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  minor_breaks = date_breaks("1 month")) +
scale_y_continuous(name = "", labels = comma_format(digits = 1))
ggsave("graphs/extrapolated-responses.pdf", width = 6, height = 5,
  dpi = 72)

ggplot(b, aes(x = as.Date(date), y = (na + nb) / nabcd)) +
geom_line() +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  minor_breaks = date_breaks("1 month")) +
scale_y_continuous(name = "", limit = c(0, 1), labels = percent)
ggsave("graphs/fraction-unweighted.pdf", width = 6, height = 3, dpi = 72)

x <- data.frame(date = b$date,
  #x1 = (b$ra + b$rb) * b$nabcd / (b$na + b$nb),
  x2 = ((b$ra + b$rb) * (b$ha + b$hc) *
  b$nabcd) / (b$ha * (b$na + b$nc) + (b$ha + b$hc) * b$nb))
#x <- melt(x, id = "date")
ggplot(x, aes(x = as.Date(date), y = x2 / 86400)) +
geom_line() +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  minor_breaks = date_breaks("1 month")) +
scale_y_continuous(name = "", labels = comma_format(digits = 1))
ggsave("graphs/totalresponses.pdf", width = 6, height = 3, dpi = 72)

n <- data.frame(date = b$date, na = b$na / 86400, nb = b$nb / 86400,
  nc = b$nc / 86400, nd = (b$nabcd - b$na - b$nb - b$nc) / 86400)
n <- melt(n, id = "date")
ggplot(n, aes(x = as.Date(date), y = value)) +
geom_line() +
facet_grid(variable ~ .) +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  minor_breaks = date_breaks("1 month")) +
scale_y_continuous(name = "", labels = comma_format(digits = 1))
ggsave("graphs/n.pdf", width = 6, height = 7, dpi = 100)

h <- data.frame(date = b$date, value = (b$ha + b$hc) / 86400)
ggplot(h, aes(x = as.Date(date), y = value)) +
geom_line() +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  minor_breaks = date_breaks("1 month")) +
scale_y_continuous(name = "", labels = comma_format(digits = 1))
ggsave("graphs/history-bytes.pdf", width = 6, height = 3, dpi = 100)

h <- data.frame(date = b$date, ha = b$ha / 86400, hc = b$hc / 86400)
h <- melt(h, id = "date")
ggplot(h, aes(x = as.Date(date), y = value)) +
geom_line() +
facet_grid(variable ~ .) +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  minor_breaks = date_breaks("1 month")) +
scale_y_continuous(name = "", labels = comma_format(digits = 1))
ggsave("graphs/h.pdf", width = 6, height = 5, dpi = 100)

r <- data.frame(date = b$date, ra = b$ra / 86400, rb = b$rb / 86400)
r <- melt(r, id = "date")
ggplot(r, aes(x = as.Date(date), y = value)) +
geom_line() +
facet_grid(variable ~ .) +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  minor_breaks = date_breaks("1 month")) +
scale_y_continuous(name = "", labels = comma_format(digits = 1))
ggsave("graphs/r.pdf", width = 6, height = 5, dpi = 100)

x <- data.frame(date = b$date,
  value = ((b$ra + b$rb) * (b$ha + b$hc) *
  b$nabcd) / (b$ha * (b$na + b$nc) + (b$ha + b$hc) * b$nb) / 864000,
  stringsAsFactors = FALSE)
x <- melt(x, id = "date")
ggplot(x, aes(x = as.Date(date), y = value)) +
geom_line() +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  minor_breaks = date_breaks("1 month")) +
scale_y_continuous(name = "", labels = comma_format(digits = 1))
ggsave("graphs/totalusers.pdf", width = 6, height = 3, dpi = 100)
x <- x[x$date >= '2012-07-01', ]
#max_y <- max(x$value / 864000, na.omit = FALSE)
ggplot(x, aes(x = as.Date(date), y = value)) +
geom_line() +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  breaks = date_breaks("1 month"),
  minor_breaks = date_breaks("1 week")) +
scale_y_continuous(name = "", #limit = c(0, max_y),
  labels = comma_format(digits = 1))
ggsave("graphs/totalusers-q3-2012.pdf", width = 6, height = 3, dpi = 100)

ggplot(b, aes(x = as.Date(date), y = consensuses)) +
geom_point() +
geom_hline(yintercept = 19.5, linetype = 2) +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  minor_breaks = date_breaks("1 month")) +
scale_y_continuous(name = "", labels = comma_format(digits = 1))
ggsave("graphs/consensuses.pdf", width = 6, height = 3, dpi = 100)

x <- data.frame(date = b$date,
  value = (b$sy * (b$ra + b$rb) * (b$ha + b$hc) *
  b$nabcd) / (b$ha * (b$na + b$nc) + (b$ha + b$hc) * b$nb))
x <- melt(x, id = "date")
ggplot(x, aes(x = as.Date(date), y = value / 864000)) +
geom_line() +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  minor_breaks = date_breaks("1 month")) +
scale_y_continuous(name = "", labels = comma_format(digits = 1))
ggsave("graphs/syusers.pdf", width = 6, height = 3, dpi = 100)

u <- read.csv("bridge-users.csv", stringsAsFactors = FALSE)
u <- u[u$date >= "2011-07-01" & u$date <= "2012-09-30", ]
u <- data.frame(date = u$date, all = u$all)
ggplot(u, aes(x = as.Date(date), y = all)) +
geom_line() +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  minor_breaks = date_breaks("1 month")) +
scale_y_continuous(name = "", labels = comma_format(digits = 1))
ggsave("graphs/totalusers-oldapproach.pdf", width = 6, height = 3,
  dpi = 100)

u <- read.csv("bridge-users.csv", stringsAsFactors = FALSE)
u <- u[u$date >= "2011-07-01" & u$date <= "2012-09-30", ]
u <- data.frame(date = u$date, value = u$all,
  variable = "old approach based on\nunique IP addresses",
  stringsAsFactors = FALSE)
x <- data.frame(date = b$date,
  value = ((b$ra + b$rb) * (b$ha + b$hc) *
  b$nabcd) / (b$ha * (b$na + b$nc) + (b$ha + b$hc) * b$nb) / 864000,
  variable = "new approach based on\ndirectory requests",
  stringsAsFactors = FALSE)
u <- rbind(u, x)
ggplot(u, aes(x = as.Date(date), y = value)) +
geom_line() +
facet_grid(variable ~ ., scales = "free_y") +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  minor_breaks = date_breaks("1 month")) +
scale_y_continuous(name = "", labels = comma_format(digits = 1))
ggsave("graphs/compare-totalusers.pdf", width = 6, height = 4,
  dpi = 100)
u <- u[u$date >= '2012-07-01', ]
ggplot(u, aes(x = as.Date(date), y = value)) +
geom_line() +
facet_grid(variable ~ ., scales = "free_y") +
scale_x_date(name = "",
  labels = date_format("%b %Y"),
  breaks = date_breaks("1 month"),
  minor_breaks = date_breaks("1 week")) +
scale_y_continuous(name = "", labels = comma_format(digits = 1))
ggsave("graphs/compare-totalusers-q3-2012.pdf", width = 6, height = 4,
  dpi = 100)

