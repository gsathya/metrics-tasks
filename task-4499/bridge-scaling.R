library(ggplot2)
d <- read.csv("bridge-scaling.csv", header = TRUE)
t <- d[d$variable == "1tarball", ]
b <- d[d$variable == "2bridgedb", ]
m <- d[d$variable == "3metricsdb", ]
d <- rbind(
  data.frame(x = t$x, y = t$y, colour = t$colour,
    variable = "Tarball size in GiB/day"),
  data.frame(x = b$x, y = b$y, colour = b$colour,
    variable = "BridgeDB time in min"),
  data.frame(x = m$x, y = m$y, colour = m$colour,
    variable = "metrics-db time in min"))
ggplot(d, aes(x = x, y = y, colour = colour)) +
geom_line(colour = "black") +
geom_point() +
facet_grid(variable ~ ., scales = "free_y") +
scale_x_continuous(name = "\nRunning bridges (2012-01-31 = 838, red)") +
scale_y_continuous(name = "") +
scale_colour_manual(name = "", values = c("black", "red")) +
opts(legend.position = "none",
  title = "Scalability of Tor's bridge infrastructure\n")
ggsave("bridge-scaling-graph.pdf", width = 7, height = 6, dpi = 100)

