library(ggplot2)
b <- read.csv("stats-coverage.csv")
b <- aggregate(list(totalwritten = b$totalwritten,
  bridgewritten = b$bridgewritten, geoipwritten = b$geoipwritten),
  by = list(date = b$date), sum)
b <- data.frame(date = b$date, value = b$bridgewritten / b$totalwritten)
ggplot(b, aes(x = as.Date(date), y = value)) +
geom_line() +
scale_x_date(name = "", major = "3 months", minor = "1 month",
  format = "%b %Y", limits = as.Date(c("2010-10-01", "2011-09-30"))) +
scale_y_continuous(name = "", limits = c(0, 1), formatter = "percent") +
scale_colour_hue(name = "") +
opts(title = "Fraction of bridges reporting statistics\n")
ggsave("stats-coverage-bridges.png", width = 8, height = 6, dpi = 72)

