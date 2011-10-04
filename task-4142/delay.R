library(ggplot2)
d <- read.csv("delay.csv")
d <- data.frame(published = d$published,
  dirreqstatsend = d$dirreqstatsend,
  delay = as.POSIXlt(d$published) - as.POSIXlt(d$dirreqstatsend))
m <- mean(as.numeric(d$delay)) / (60 * 60)
ggplot(d, aes(x = as.numeric(delay) / (60 * 60))) +
geom_histogram(binwidth = 1, fill = "purple2",
  colour = alpha("white", 0.5)) +
scale_x_continuous(name = paste("\nDelay between finishing a",
 "dirreq-stats interval and first publishing its results",
 "(red line = mean value)"), minor_breaks = 1) +
scale_y_continuous(name = "Number of finished dirreq-stats intervals\n") +
geom_vline(xintercept = m, colour = "red", size = 1.5) +
ggsave("delay.png", width = 8, height = 5, dpi = 100)

