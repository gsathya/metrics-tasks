library(ggplot2)
library(reshape)
d <- read.csv("entropy.csv", header = FALSE,
  col.names = c("validafter", "all", "exit", "guard", "country"))
d <- aggregate(
  list(all = d$all, exit = d$exit, guard = d$guard, country = d$country),
  by = list(date = as.Date(d$validafter, origin = "1970-01-01 00:00:00")),
  FUN = median)
d <- melt(d, "date")
ggplot(d, aes(x = date, y = value, colour = variable)) +
geom_line() +
scale_x_date(name = "\nDate") +
scale_y_continuous(name = "Degree of Anonymity\n") +
scale_colour_hue(name = "Relays",
  breaks = c("all", "exit", "guard", "country"),
  labels = c("All relays", "All exits", "All guards", "All countries"))
ggsave("entropy.png", width = 8, height = 6, dpi = 100)


