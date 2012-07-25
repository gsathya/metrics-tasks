library(ggplot2)
library(reshape)
library(scales)

d <- read.csv("entropy.csv", header = FALSE,
  col.names = c("validafter", "all", "max_all", "exit", "max_exit",
  "guard", "max_guard", "country", "max_country", "as", "max_as"))

e <- aggregate(
  list(all = d$all / d$max_all, exit = d$exit / d$max_exit,
  guard = d$guard / d$max_guard, country = d$country / d$max_country,
  as = d$as / d$max_as),
  by = list(date = as.Date(d$validafter, origin = "1970-01-01 00:00:00")),
  FUN = median)
e <- melt(e, "date")
e <- data.frame(date = e$date, variable =
  ifelse(e$variable == "all", "All relays",
  ifelse(e$variable == "exit", "All exits",
  ifelse(e$variable == "guard", "All guards",
  ifelse(e$variable == "country", "All countries",
  "All ASes")))), value = e$value)
ggplot(e, aes(x = date, y = value)) +
geom_line() +
facet_wrap(~ variable) +
scale_x_date(name = "\nDate") +
scale_y_continuous(name = "Degree of anonymity\n", limits = c(0, 1),
  labels = percent)
ggsave("degree-of-anonymity.png", width = 8, height = 6, dpi = 100)

f <- aggregate(list(all = d$all, max_all = d$max_all, exit = d$exit,
  max_exit = d$max_exit, guard = d$guard, max_guard = d$max_guard,
  country = d$country, max_country = d$max_country, as = d$as,
  max_as = d$max_as),
  by = list(date = as.Date(d$validafter, origin = "1970-01-01 00:00:00")),
  FUN = median)
f <- rbind(
  data.frame(date = f$date, entropy = f$all, max = f$max_all,
    type = "All relays"),
  data.frame(date = f$date, entropy = f$exit, max = f$max_exit,
    type = "All exits"),
  data.frame(date = f$date, entropy = f$guard, max = f$max_guard,
    type = "All guards"),
  data.frame(date = f$date, entropy = f$country, max = f$max_country,
    type = "All countries"),
  data.frame(date = f$date, entropy = f$as, max = f$max_as,
    type = "All ASes"))
f <- melt(f, c("date", "type"))
ggplot(f, aes(x = date, y = value, colour = variable)) +
geom_line() +
facet_wrap(~ type) +
scale_x_date(name = "\nDate") +
scale_y_continuous(name = "Entropy and maximum entropy\n",
  limits = c(0, max(f$value))) +
opts(legend.position = "none")
ggsave("entropy.png", width = 8, height = 6, dpi = 100)


g <- aggregate(list(all = d$all, max_all = d$max_all, exit = d$exit,
  max_exit = d$max_exit, guard = d$guard, max_guard = d$max_guard,
  country = d$country, max_country = d$max_country, as = d$as,
  max_as = d$max_as),
  by = list(date = as.Date(d$validafter, origin = "1970-01-01 00:00:00")),
  FUN = median)
g <- rbind(
  data.frame(date = g$date, entropy = 2^g$all, max = 2^g$max_all,
    type = "All relays"),
  data.frame(date = g$date, entropy = 2^g$exit, max = 2^g$max_exit,
    type = "All exits"),
  data.frame(date = g$date, entropy = 2^g$guard, max = 2^g$max_guard,
    type = "All guards"))
g <- melt(g, c("date", "type"))
ggplot(g, aes(x = date, y = value, colour = variable)) +
geom_line() +
facet_wrap(~ type) +
scale_x_date(name = "\nDate") +
scale_y_continuous(name = "Number of relays and Number of fair relays\n",
  limits = c(0, max(g$value))) +
opts(legend.position = "none")
ggsave("relays_fair.png", width = 8, height = 6, dpi = 100)

