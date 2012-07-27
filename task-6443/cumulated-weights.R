require(ggplot2)
require(scales)
require(reshape)

cw <- read.csv("cumulated-weights.csv", stringsAsFactors = FALSE)
v <- cw
v <- v[v$weight_type == "consensus weights", c(1, 3, 4)]
v <- sort(as.POSIXlt(unique(v$validafter), tz = "UTC"),
  decreasing = TRUE)
now <- week <- month <- threemonths <- year <- v[1]
week$mday <- week$mday - 7
month$mon <- month$mon - 1
threemonths$mon <- threemonths$mon - 3
year$year <- year$year - 1
v <- c(
  format((v[v <= year])[1], usetz = FALSE),
  format((v[v <= threemonths])[1], usetz = FALSE),
  format((v[v <= month])[1], usetz = FALSE),
  format((v[v <= week])[1], usetz = FALSE),
  format(now, usetz = FALSE))
v <- cw[cw$validafter %in% v, ]
v <- v[v$weight_type == "consensus weights", c(1, 3, 4)]
v <- data.frame(x = v$top_relays, y = v$total_exit_probability,
  colour = factor(v$validafter,
  levels = rev(levels(factor(v$validafter)))))
ggplot(v, aes(x = x, y = y, colour = colour)) +
geom_point() +
geom_line() +
scale_x_continuous(name = "\nTop-x relays by exit probability") +
scale_y_continuous(name = "Total exit probability\n", labels = percent) +
scale_colour_hue(name = "",
  labels = c(format(as.Date(now), format = "%B %d, %Y"), "1 week before",
  "1 month before", "3 months before", "1 year before")) +
opts(title = paste("Probability of selecting one of the top-x relays for",
  "the exit position\n"))
ggsave("exit-probability-cdf-a.png", width = 8, height = 5, dpi = 100)

c <- cw
c <- c[c$weight_type == "consensus weights", c(1, 3, 4)]
c <- c[c$top_relays %in% c(1, 2, 5, 10, 20, 50), ]
c <- aggregate(list(total_exit_probability = c$total_exit_probability),
  by = list(date = as.Date(cut.Date(as.Date(c$validafter,
  origin = "1970-01-01 00:00:00"), "week")),
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
ggsave("exit-probability-cdf-b.png", width = 8, height = 5, dpi = 100)

iw <- read.csv("inverse-cumulated-weights.csv", stringsAsFactors = FALSE)
i <- iw
i <- i[i$weight_type == "consensus weights", c(1, 3, 4)]
i <- i[i$total_exit_probability %in% factor(c(0.3, 0.4, 0.5, 0.6, 0.7)), ]
i <- aggregate(list(top_relays = i$top_relays),
  by = list(date = as.Date(cut.Date(as.Date(i$validafter,
  origin = "1970-01-01 00:00:00"), "week")),
  total_exit_probability = i$total_exit_probability), FUN = mean)
ggplot(i, aes(x = date, y = top_relays,
  colour = paste(100 * total_exit_probability, "%", sep = ""))) +
geom_line() +
scale_x_date(name = "") +
scale_y_continuous(name = "Top-x relays by exit probability\n") +
scale_colour_hue(name = "Total exit probability of top-x relays") +
opts(title = paste("Number of relays making up the top-x for a given",
  "total exit probability\n"), legend.position = "bottom")
ggsave("exit-probability-cdf-c.png", width = 8, height = 5, dpi = 100)

v <- cw
max_validafter <- max(v$validafter)
v <- v[v$validafter == max_validafter, ]
ggplot(v, aes(x = top_relays, y = total_exit_probability,
  colour = weight_type)) +
geom_point() +
geom_line() +
scale_x_continuous(name = "\nTop-x relays by exit probability") +
scale_y_continuous(name = "Total exit probability\n", labels = percent) +
scale_colour_hue(name = "") +
opts(title = paste("Probability of selecting one of the top-x relays\nfor",
  "the exit position on", strftime(max_validafter, format = "%B %d, %Y")),
  legend.position = "top")
ggsave("exit-probability-cdf-d.png", width = 8, height = 5, dpi = 100)

c <- cw
c <- c[c$top_relays == 10, ]
c <- aggregate(list(total_exit_probability = c$total_exit_probability),
  by = list(date = as.Date(cut.Date(as.Date(c$validafter,
  origin = "1970-01-01 00:00:00"), "week")),
  weight_type = c$weight_type), FUN = mean)
ggplot(c, aes(x = date, y = total_exit_probability,
  colour = as.factor(weight_type))) +
geom_line() +
scale_x_date(name = "") +
scale_y_continuous(name = "Total exit probability of top-10 relays\n",
  #limits = c(0, 1), labels = percent) +
  labels = percent) +
scale_colour_hue(name = "") +
opts(title = paste("Probability of selecting one of the top-10 relays for",
  "the exit position"), legend.position = "top")
ggsave("exit-probability-cdf-e.png", width = 8, height = 5, dpi = 100)

i <- iw
i <- i[i$total_exit_probability == factor(0.5), ]
i <- aggregate(list(top_relays = i$top_relays),
  by = list(date = as.Date(cut.Date(as.Date(i$validafter,
  origin = "1970-01-01 00:00:00"), "week")),
  weight_type = i$weight_type), FUN = mean)
ggplot(i, aes(x = date, y = top_relays,
  colour = weight_type)) +
geom_line() +
scale_x_date(name = "") +
scale_y_continuous(name = "Top-x relays by exit probability\n") +
scale_colour_hue(name = "") +
opts(title = paste("Number of relays making up the top-x for 50%",
  "total exit probability"), legend.position = "top")
ggsave("exit-probability-cdf-f.png", width = 8, height = 5, dpi = 100)

