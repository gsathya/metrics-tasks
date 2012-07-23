require(ggplot2)
require(scales)
require(reshape)

c <- read.csv("cumulated-weights.csv")
c <- c[c$top_relays %in% c(1, 2, 3, 5, 10, 15, 20, 30, 50), ]
c <- aggregate(list(total_exit_probability = c$total_exit_probability),
  by = list(date = as.Date(c$validafter, origin = "1970-01-01 00:00:00"),
  top_relays = c$top_relays), FUN = mean)
ggplot(c, aes(x = date, y = total_exit_probability,
  colour = as.factor(top_relays))) +
geom_line() +
scale_x_date(name = "") +
scale_y_continuous(name = "Total exit probability of top-x relays\n",
  limits = c(0, 1), labels = percent) +
scale_colour_hue(name = "Top-x relays by exit probability") +
opts(title = paste("Probability of selecting one of the top-x relays for",
  "the exit position\n"), legend.position = "bottom")
ggsave("exit-probability-cdf.png", width = 8, height = 5, dpi = 100)

i <- read.csv("inverse-cumulated-weights.csv")
i <- i[i$total_exit_probability > 0.1 & i$total_exit_probability < 0.9, ]
i <- aggregate(list(top_relays = i$top_relays),
  by = list(date = as.Date(i$validafter, origin = "1970-01-01 00:00:00"),
  total_exit_probability = i$total_exit_probability), FUN = mean)
ggplot(i, aes(x = date, y = top_relays,
  colour = paste(100 * total_exit_probability, "%", sep = ""))) +
geom_line() +
scale_x_date(name = "") +
scale_y_continuous(name = "Top-x relays by exit probability\n") +
scale_colour_hue(name = "Total exit probability of top-x relays") +
opts(title = paste("Number of relays making up the top-x for a given",
  "total exit probability\n"), legend.position = "bottom")
ggsave("exit-probability-icdf.png", width = 8, height = 5, dpi = 100)

