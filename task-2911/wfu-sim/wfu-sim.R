library(ggplot2)
data <- read.csv("wfu-sim.csv", stringsAsFactors = FALSE)

d <- data[data$time >= '2010' & data$time < '2011', ]
d <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)), mean)
d <- rbind(
  data.frame(x = d$guards9000, y = d$wfu9000, sim = "90 %"),
  data.frame(x = d$guards9500, y = d$wfu9500, sim = "95 %"),
  data.frame(x = d$guards9800, y = d$wfu9800, sim = "98 % (default)"),
  data.frame(x = d$guards9900, y = d$wfu9900, sim = "99 %"),
  data.frame(x = d$guards9990, y = d$wfu9990, sim = "99.9 %"))
ggplot(d, aes(x = x / 10000.0, y = y / 10000.0)) +
geom_path() +
facet_wrap(~ sim) +
scale_x_continuous("\nFraction of relays meeting WFU requirement",
  formatter = "percent") +
scale_y_continuous("Mean WFU in the future\n", formatter = "percent")
ggsave(filename = "wfu-sim.pdf", width = 8, height = 5, dpi = 100)

## Commented out, because graph is meaningless in b/w.
#d <- data[data$time >= '2010' & data$time < '2011', ]
#d <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)), mean)
#d <- rbind(
#  data.frame(x = d$guards9000, y = d$wfu9000, sim = "90 %"),
#  data.frame(x = d$guards9500, y = d$wfu9500, sim = "95 %"),
#  data.frame(x = d$guards9800, y = d$wfu9800, sim = "98 % (default)"),
#  data.frame(x = d$guards9900, y = d$wfu9900, sim = "99 %"),
#  data.frame(x = d$guards9990, y = d$wfu9990, sim = "99.9 %"))
#ggplot(d, aes(x = x / 10000.0, y = y / 10000.0, colour = sim)) +
#geom_path() +
#scale_x_continuous("\nFraction of relays meeting WFU requirement",
#  formatter = "percent") +#, trans = "reverse") +
#scale_y_continuous("Mean WFU    \nin the future    ",
#  formatter = "percent") +
#scale_colour_hue("Required WFU") +
#opts(axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
#  hjust = 0.5),
#  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
#  hjust = 1))
#ggsave(filename = "wfu-sim.pdf", width = 8, height = 5, dpi = 100)

## Commented out, because the time plot is not as useful as expected.
#simulations <- paste("wfu", rev(c(9000, 9200, 9400, 9600, 9800)),
#  sep = "")
#d <- data[data$time >= '2010' & data$time < '2011',
#  c("time", simulations)]
#d <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)), mean)
#d <- melt(d, id.vars = 1)
#ggplot(d, aes(x = date, y = value / 10000.0, colour = variable)) +
#geom_line() +
#scale_x_date("", major = "3 months", minor = "1 month",
#  format = "%b %Y") +
#scale_y_continuous("Empirical future WFU\n", formatter = "percent") +
#scale_colour_hue("Required past WFU\n", breaks = simulations,
#  labels = paste(as.numeric(substr(simulations, 4, 9)) / 100.0, "%"))
#ggsave(filename = "wfu-sim-time.pdf", width = 8, height = 5, dpi = 100)

