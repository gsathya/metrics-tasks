library(ggplot2)
library(scales)
b <- read.csv("stats-coverage.csv")
b <- aggregate(list(
  totalwritten = b$totalwritten, totalseconds = b$totalseconds,
  totalrunning = b$totalrunning, bridgewritten = b$bridgewritten,
  bridgeseconds = b$bridgeseconds, bridgerunning = b$bridgerunning,
  geoipwritten = b$geoipwritten, geoipseconds = b$geoipseconds,
  geoiprunning = b$geoiprunning), by = list(date = as.Date(b$date)), sum)
b <- rbind(data.frame(date = b$date, variable = "by written bytes",
    value = (b$bridgewritten + b$geoipwritten) / b$totalwritten),
  data.frame(date = b$date, variable = "by uptime (bandwidth history)",
    value = (b$bridgeseconds + b$geoipseconds) / b$totalseconds),
  data.frame(date = b$date, variable = "by uptime (Running flag)",
    value = (b$bridgerunning + b$geoiprunning) / b$totalrunning))
b <- b[b$date >= as.Date("2010-10-01") & b$date < as.Date("2012-04-01"), ]
ggplot(b, aes(x = date, y = value)) +
geom_line() +
facet_grid(variable ~ .) +
scale_x_date(name = "") +
scale_y_continuous(name = "", limits = c(0, 1), labels = percent) +
scale_colour_hue(name = "") +
opts(title = "Fraction of bridges reporting statistics\n")
ggsave("stats-coverage-bridges.png", width = 8, height = 7, dpi = 72)

