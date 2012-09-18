library(ggplot2)
library(reshape)
library(scales)

d <- read.csv("entropy.csv", header = FALSE,
  col.names = c("validafter", "min_cw", "relays", "all", "max_all",
  "exit", "max_exit", "guard", "max_guard", "country", "max_country",
  "as", "max_as"), stringsAsFactor = FALSE)
max_validafter <- max(d$validafter)
d <- d[d$validafter == max_validafter, ]

e <- d[d$min_cw <= 10000, ]
e <- data.frame(min_cw = e$min_cw, all = e$all / e$max_all,
  exit = e$exit / e$max_exit, guard = e$guard / e$max_guard,
  country = e$country / e$max_country, as = e$as / e$max_as)
e <- melt(e, "min_cw")
e <- data.frame(min_cw = e$min_cw, variable =
  ifelse(e$variable == "all", "All relays",
  ifelse(e$variable == "exit", "All exits",
  ifelse(e$variable == "guard", "All guards",
  ifelse(e$variable == "country", "All countries",
  "All ASes")))), value = e$value)
ggplot(e, aes(x = min_cw, y = value)) +
geom_line() +
facet_wrap(~ variable) +
scale_x_continuous(name = "\nMinimum consensus weight") +
scale_y_continuous(name = "", limits = c(0, 1), labels = percent) +
opts(title = paste("Degree of anonymity based on consensus from ",
  max_validafter, "\n", sep = ""))
ggsave("degree-of-anonymity-min-cw.png", width = 8, height = 6, dpi = 100)

f <- d[d$min_cw <= 10000, ]
f <- rbind(
  data.frame(min_cw = f$min_cw, entropy = f$all, max = f$max_all,
    type = "All relays"),
  data.frame(min_cw = f$min_cw, entropy = f$exit, max = f$max_exit,
    type = "All exits"),
  data.frame(min_cw = f$min_cw, entropy = f$guard, max = f$max_guard,
    type = "All guards"),
  data.frame(min_cw = f$min_cw, entropy = f$country, max = f$max_country,
    type = "All countries"),
  data.frame(min_cw = f$min_cw, entropy = f$as, max = f$max_as,
    type = "All ASes"))
f <- melt(f, c("min_cw", "type"))
ggplot(f, aes(x = min_cw, y = value, colour = variable)) +
geom_line() +
facet_wrap(~ type) +
scale_x_continuous(name = "\nMinimum consensus weight") +
scale_y_continuous(name = "", limits = c(0, max(f$value))) +
opts(title = paste("Entropy and maximum entropy based on consensus from ",
  max_validafter, "\n", sep = ""), legend.position = "none")
ggsave("entropy-min-cw.png", width = 8, height = 6, dpi = 100)

