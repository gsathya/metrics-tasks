library(ggplot2)
b <- read.csv("stats-coverage.csv")
b <- aggregate(list(
  totalwritten = b$totalwritten, totalseconds = b$totalseconds,
  bridgewritten = b$bridgewritten, bridgeseconds = b$bridgeseconds,
  geoipwritten = b$geoipwritten, geoipseconds = b$geoipseconds),
  by = list(date = as.Date(b$date)), sum)
b <- rbind(data.frame(date = b$date, variable = "by written bytes",
    value = (b$bridgewritten + b$geoipwritten) / b$totalwritten),
  data.frame(date = b$date, variable = "by uptime seconds",
    value = (b$bridgeseconds + b$geoipseconds) / b$totalseconds))
ggplot(b, aes(x = date, y = value)) +
geom_line() +
facet_grid(variable ~ .) +
scale_x_date(name = "", major = "3 months", minor = "1 month",
  format = "%b %Y", limits = as.Date(c("2010-10-01", "2011-09-30"))) +
scale_y_continuous(name = "", limits = c(0, 1), formatter = "percent") +
scale_colour_hue(name = "") +
opts(title = "Fraction of bridges reporting statistics\n")
ggsave("stats-coverage-bridges.png", width = 8, height = 6, dpi = 72)

