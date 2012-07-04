library(ggplot2)
d <- read.csv("entropy.csv", header = FALSE,
  col.names = c("validafter", "entropy"))
ggplot(d, aes(x = as.POSIXct(validafter), y = entropy)) +
geom_line() +
scale_x_datetime(name = "\nDate") +
scale_y_continuous(name = "Entropy\n")
ggsave("entropy.png", width = 8, height = 6, dpi = 100)


