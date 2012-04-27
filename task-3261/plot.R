library(ggplot2)
library(scales)
library(reshape)
a <- read.csv("aggregated.csv", stringsAsFactors = FALSE)

e <- a
e <- data.frame(date = as.Date(e$date), case = ifelse(
  e$reported == "true", ifelse(e$discarded == "false", "case1", "case2"),
  "case3"), bridges = e$bridges)
e <- aggregate(list(bridges = e$bridges),
  by = list(date = e$date, case = e$case), FUN = sum)
e <- cast(e, date ~ case)
sums <- e$case1 + e$case2 + e$case3
e <- data.frame(date = e$date, case1 = e$case1 / sums,
  case2 = e$case2 / sums, case3 = e$case3 / sums, stringsAsFactors = FALSE)
e <- melt(e, "date")
e <- data.frame(date = e$date, variable = ifelse(e$variable == "case1",
  "reported and used", ifelse(e$variable == "case2",
  "reported and discarded", "not reported")), value = e$value)
ggplot(e, aes(x = as.Date(date), y = value)) +
geom_line() +
facet_grid(variable ~ .) +
scale_x_date(name = "") +
scale_y_continuous(name = "", labels = percent) +
opts(title = "Fraction of bridge usage statistics that were...\n")
ggsave("reported-bridge-statistics.png", width = 8, height = 6, dpi = 120)

d <- a
d <- d[d$reported == "false", ]
d <- data.frame(date = d$date, reason = d$reason, value = d$bridges)
d <- cast(d, date ~ reason)
d <- data.frame(date = d$date, case1 = d$lessthan24h / sums,
  case2 = d$publdelay / sums, case3 = d$other / sums)
d <- melt(d, "date")
d <- data.frame(date = d$date, variable = ifelse(d$variable == "case1",
  "Less than 24h uptime", ifelse(d$variable == "case2",
  "Publication delay", "Other reason")), value = d$value)
ggplot(d, aes(x = as.Date(date), y = value)) +
geom_line() +
facet_grid(variable ~ .) +
scale_x_date(name = "") +
scale_y_continuous(name = "", labels = percent) +
opts(title = "Reasons for bridges not reporting usage statistics\n")
ggsave("bridge-statistics-nonreported.png", width = 8, height = 6,
  dpi = 120)

b <- a
b <- b[b$discarded == "true", ]
b <- data.frame(date = b$date, reason = b$reason, value = b$bridges)
b <- cast(b, date ~ reason)
b <- data.frame(date = b$date, case1 = b$geoip022 / sums,
  case2 = b$nogeoipfile / sums, case3 = b$runasrelay / sums)
b <- melt(b, "date")
b <- data.frame(date = b$date, variable = ifelse(b$variable == "case1",
  "0.2.2.x geoip-stats bug", ifelse(b$variable == "case2",
  "missing geoip file", "Run as non-bridge relay")), value = b$value)
ggplot(b, aes(x = as.Date(date), y = value)) +
geom_line() +
facet_grid(variable ~ .) +
scale_x_date(name = "") +
scale_y_continuous(name = "", labels = percent) +
opts(title = "Reasons for discarding reported usage statistics\n")
ggsave("bridge-statistics-discarded.png", width = 8, height = 6,
  dpi = 120)

